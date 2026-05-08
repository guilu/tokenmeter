package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.domain.analyzer.LanguageStatistics;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
  void deletesLanguageStatsThroughForeignKeyRelationship() {
    RepositoryAnalysisResult saved = persistenceService.save(sampleResult());

    repository.deleteById(saved.id());
    repository.flush();

    assertThat(repository.findById(saved.id())).isEmpty();
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
            List.of(),
            Map.of(
                "Java", new LanguageTokenMetrics("Java", 2, 25),
                "TypeScript", new LanguageTokenMetrics("TypeScript", 1, 15))),
        List.of(
            new ModelCostEstimate(
                AiProvider.OPENAI,
                "gpt-4o",
                CostEstimationMode.RAW,
                40,
                0,
                40,
                new BigDecimal("0.000000"),
                new BigDecimal("0.000400"),
                new BigDecimal("0.000400"),
                "test formula")));
  }
}
