package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.domain.analyzer.LanguageStatistics;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotId;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenizationPrecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAnalysisPersistenceService.class)
class JpaAnalysisPersistenceServiceTest {
  @Autowired private JpaAnalysisPersistenceService persistenceService;
  @Autowired private AnalysisJpaRepository repository;

  @Test
  void persistsCompletedAnalysisAndLanguageStatistics() {
    RepositoryAnalysisResult saved = persistenceService.save(sampleResult());

    assertThat(saved.id()).isNotNull();
    assertThat(saved.createdAt()).isNotNull();
    assertThat(repository.findById(saved.id())).isPresent();
    assertThat(saved.scan().languages().get("Java").files()).isEqualTo(2);
    assertThat(saved.tokenization().languages().get("Java").tokens()).isEqualTo(25);
    assertThat(saved.costEstimates()).hasSize(1);
    assertThat(saved.costEstimates().getFirst().totalCost()).isEqualByComparingTo("0.000400");
  }

  @Test
  void retrievesAnalysisById() {
    RepositoryAnalysisResult saved = persistenceService.save(sampleResult());

    assertThat(persistenceService.findById(saved.id()))
        .isPresent()
        .get()
        .satisfies(
            result -> {
              assertThat(result.repositoryUrl()).isEqualTo("https://github.com/guilu/tokenmeter");
              assertThat(result.scan().totalFiles()).isEqualTo(3);
              assertThat(result.tokenization().totalTokens()).isEqualTo(40);
              assertThat(result.scan().languages()).containsKeys("Java", "TypeScript");
              assertThat(result.costEstimates()).hasSize(1);
              assertThat(result.costEstimates().getFirst().mode())
                  .isEqualTo(CostEstimationMode.RAW);
            });
  }

  @Test
  void returnsEmptyForNonexistentAnalysis() {
    assertThat(persistenceService.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void findsLatestSuccessIdByRepositoryUrlAndSnapshot() {
    String snapshotId = "v1:" + "a".repeat(64);
    PricingSnapshotHandle handle =
        new PricingSnapshotHandle(
            new PricingSnapshotId(snapshotId),
            PricingSource.REMOTE,
            Instant.parse("2026-05-25T12:00:00Z"),
            List.of());
    RepositoryAnalysisResult older = persistenceService.save(sampleResultWithPricing(handle));
    RepositoryAnalysisResult newer = persistenceService.save(sampleResultWithPricing(handle));

    Optional<UUID> found =
        persistenceService.findLatestSuccessIdFor(
            "https://github.com/guilu/tokenmeter", snapshotId);

    assertThat(found).isPresent();
    assertThat(List.of(older.id(), newer.id())).contains(found.get());
  }

  @Test
  void findLatestSuccessIdReturnsEmptyForDifferentSnapshot() {
    PricingSnapshotHandle handle =
        new PricingSnapshotHandle(
            new PricingSnapshotId("v1:" + "b".repeat(64)),
            PricingSource.REMOTE,
            Instant.parse("2026-05-25T12:00:00Z"),
            List.of());
    persistenceService.save(sampleResultWithPricing(handle));

    assertThat(
            persistenceService.findLatestSuccessIdFor(
                "https://github.com/guilu/tokenmeter", "v1:" + "c".repeat(64)))
        .isEmpty();
  }

  @Test
  void deletesLanguageStatsThroughForeignKeyRelationship() {
    RepositoryAnalysisResult saved = persistenceService.save(sampleResult());

    repository.deleteById(saved.id());
    repository.flush();

    assertThat(repository.findById(saved.id())).isEmpty();
  }

  // -----------------------------------------------------------------------
  // TKM-72 — findLatestSuccessIdFor(String) single-arg overload (by-repo badge)
  // -----------------------------------------------------------------------

  @Test
  void findLatestSuccessIdForUrl_returnsNewerUuidWhenMultipleSuccessRowsExist() {
    Instant older = Instant.parse("2026-06-01T10:00:00Z");
    Instant newer = Instant.parse("2026-06-01T11:00:00Z");
    RepositoryAnalysisResult savedOlder =
        persistenceService.save(sampleResultAt("https://github.com/acme/myrepo", older));
    RepositoryAnalysisResult savedNewer =
        persistenceService.save(sampleResultAt("https://github.com/acme/myrepo", newer));

    Optional<UUID> found =
        persistenceService.findLatestSuccessIdFor("https://github.com/acme/myrepo");

    assertThat(found).isPresent();
    assertThat(found.get()).isEqualTo(savedNewer.id());
    assertThat(found.get()).isNotEqualTo(savedOlder.id());
  }

  @Test
  void findLatestSuccessIdForUrl_returnsEmptyWhenNoRowExistsForUrl() {
    Optional<UUID> found =
        persistenceService.findLatestSuccessIdFor("https://github.com/acme/unknown");
    assertThat(found).isEmpty();
  }

  @Test
  void findLatestSuccessIdForUrl_returnsEmptyForNullUrl() {
    Optional<UUID> found = persistenceService.findLatestSuccessIdFor((String) null);
    assertThat(found).isEmpty();
  }

  @Test
  void findLatestSuccessIdForUrl_ignoresDifferentRepositoryUrl() {
    persistenceService.save(sampleResultAt("https://github.com/acme/other", Instant.now()));

    Optional<UUID> found =
        persistenceService.findLatestSuccessIdFor("https://github.com/acme/myrepo");
    assertThat(found).isEmpty();
  }

  // -----------------------------------------------------------------------
  // Slice C — tokenizerId + precision persistence round-trip
  // -----------------------------------------------------------------------

  @Test
  void persistsAndRetrievesTokenizerIdAndPrecision() {
    // GIVEN a ModelCostEstimate with non-null tokenizerId and precision
    RepositoryAnalysisResult input =
        resultWithEstimate(
            new ModelCostEstimate(
                AiProvider.OPENAI,
                "gpt-4o",
                CostEstimationMode.RAW,
                "openai/o200k_base",
                TokenizationPrecision.EXACT_LOCAL,
                100,
                0,
                100,
                new BigDecimal("0.000000"),
                new BigDecimal("0.001000"),
                new BigDecimal("0.001000"),
                "test formula"));

    // WHEN saved and re-read
    RepositoryAnalysisResult saved = persistenceService.save(input);
    RepositoryAnalysisResult reloaded = persistenceService.findById(saved.id()).orElseThrow();

    // THEN tokenizerId and precision are preserved
    ModelCostEstimate estimate = reloaded.costEstimates().getFirst();
    assertThat(estimate.tokenizerId()).isEqualTo("openai/o200k_base");
    assertThat(estimate.precision()).isEqualTo(TokenizationPrecision.EXACT_LOCAL);
  }

  @Test
  void persistsAndRetrievesHeuristicPrecision() {
    // GIVEN a ModelCostEstimate with HEURISTIC precision
    RepositoryAnalysisResult input =
        resultWithEstimate(
            new ModelCostEstimate(
                AiProvider.ANTHROPIC,
                "claude-3-5-sonnet-20241022",
                CostEstimationMode.RAW,
                "anthropic/cl100k_heuristic",
                TokenizationPrecision.HEURISTIC,
                95,
                0,
                95,
                new BigDecimal("0.000000"),
                new BigDecimal("0.000285"),
                new BigDecimal("0.000285"),
                "test formula"));

    RepositoryAnalysisResult saved = persistenceService.save(input);
    RepositoryAnalysisResult reloaded = persistenceService.findById(saved.id()).orElseThrow();

    ModelCostEstimate estimate = reloaded.costEstimates().getFirst();
    assertThat(estimate.tokenizerId()).isEqualTo("anthropic/cl100k_heuristic");
    assertThat(estimate.precision()).isEqualTo(TokenizationPrecision.HEURISTIC);
  }

  @Test
  void legacyNullTokenizerColumnsReadNullSafeWithoutNpe() {
    // GIVEN a ModelCostEstimate with null tokenizerId and null precision (legacy / pre-V10 row)
    RepositoryAnalysisResult input =
        resultWithEstimate(
            new ModelCostEstimate(
                AiProvider.OPENAI,
                "gpt-4o",
                CostEstimationMode.RAW,
                null,
                null,
                40,
                0,
                40,
                new BigDecimal("0.000000"),
                new BigDecimal("0.000400"),
                new BigDecimal("0.000400"),
                "test formula"));

    // WHEN saved (columns will be NULL) and re-read
    RepositoryAnalysisResult saved = persistenceService.save(input);
    RepositoryAnalysisResult reloaded = persistenceService.findById(saved.id()).orElseThrow();

    // THEN domain object reads null (no NPE, no 500)
    ModelCostEstimate estimate = reloaded.costEstimates().getFirst();
    assertThat(estimate.tokenizerId()).isNull();
    assertThat(estimate.precision()).isNull();
  }

  private static RepositoryAnalysisResult resultWithEstimate(ModelCostEstimate estimate) {
    return new RepositoryAnalysisResult(
        "https://github.com/test/repo",
        "https://github.com/test/repo.git",
        "test",
        "repo",
        new RepositoryScanResult(1, 10, 100, List.of(), Map.of()),
        new RepositoryTokenizationResult(
            "o200k_base",
            1,
            estimate.baseTokens(),
            Map.of("openai/o200k_base", estimate.baseTokens()),
            List.of(),
            Map.of()),
        List.of(estimate),
        null);
  }

  private static RepositoryAnalysisResult sampleResultWithPricing(PricingSnapshotHandle handle) {
    RepositoryAnalysisResult base = sampleResult();
    return new RepositoryAnalysisResult(
        base.repositoryUrl(),
        base.cloneUrl(),
        base.owner(),
        base.name(),
        base.scan(),
        base.tokenization(),
        base.costEstimates(),
        handle);
  }

  private static RepositoryAnalysisResult sampleResultAt(String url, Instant createdAt) {
    RepositoryAnalysisResult base = sampleResult();
    return new RepositoryAnalysisResult(
        null,
        createdAt,
        url,
        url + ".git",
        "acme",
        "myrepo",
        base.scan(),
        base.tokenization(),
        base.costEstimates(),
        null);
  }

  private static RepositoryAnalysisResult sampleResult() {
    return new RepositoryAnalysisResult(
        "https://github.com/guilu/tokenmeter",
        "https://github.com/guilu/tokenmeter.git",
        "guilu",
        "tokenmeter",
        new RepositoryScanResult(
            3,
            30,
            300,
            List.of(),
            Map.of(
                "Java", new LanguageStatistics("Java", 2, 20, 200),
                "TypeScript", new LanguageStatistics("TypeScript", 1, 10, 100))),
        new RepositoryTokenizationResult(
            "o200k_base",
            3,
            40,
            Map.of("openai/o200k_base", 40L),
            List.of(),
            Map.of(
                "Java", new LanguageTokenMetrics("Java", 2, 25),
                "TypeScript", new LanguageTokenMetrics("TypeScript", 1, 15))),
        List.of(
            new ModelCostEstimate(
                AiProvider.OPENAI,
                "gpt-4o",
                CostEstimationMode.RAW,
                null,
                null,
                40,
                0,
                40,
                new BigDecimal("0.000000"),
                new BigDecimal("0.000400"),
                new BigDecimal("0.000400"),
                "test formula")));
  }
}
