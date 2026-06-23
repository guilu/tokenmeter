package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisPersistenceService;
import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.domain.analyzer.LanguageStatistics;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test for aggregate SQL queries in {@link LeaderboardJpaRepository} using H2 in
 * PostgreSQL-compat mode. Seeds data via {@link AnalysisPersistenceService} and asserts aggregates.
 */
@SpringBootTest
class LeaderboardInsightsIntegrationTest {

  @Autowired private AnalysisPersistenceService persistenceService;
  @Autowired private LeaderboardJpaRepository leaderboardRepo;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearData() {
    // Delete in FK-safe order: jobs reference analysis, cost_estimates and language_stats reference
    // analysis
    jdbcTemplate.execute("DELETE FROM cost_estimates");
    jdbcTemplate.execute("DELETE FROM language_stats");
    jdbcTemplate.execute("DELETE FROM analysis_job");
    jdbcTemplate.execute("DELETE FROM analysis");
  }

  @Test
  void findOverviewReturnsCorrectAggregatesWithSeededData() {
    seedAnalysis("https://github.com/org/repo-a", "Java", 5_000L, 2_000L);
    seedAnalysis("https://github.com/org/repo-b", "Python", 3_000L, 1_500L);
    seedAnalysis(
        "https://github.com/org/repo-a", "Java", 4_000L, 1_800L); // same repo, 2nd analysis

    LeaderboardOverviewProjection overview = leaderboardRepo.findOverview(null, null, null);

    assertThat(overview.getTotalRepos()).isEqualTo(2L); // distinct repo URLs
    assertThat(overview.getTotalAnalyses()).isEqualTo(3L);
    assertThat(overview.getTotalTokens()).isEqualTo(5_000L + 3_000L + 4_000L);
    assertThat(overview.getTotalBytes()).isGreaterThan(0L);
  }

  @Test
  void findOverviewReturnsZerosForEmptyDataset() {
    LeaderboardOverviewProjection overview = leaderboardRepo.findOverview(null, null, null);

    assertThat(overview.getTotalRepos()).isEqualTo(0L);
    assertThat(overview.getTotalAnalyses()).isEqualTo(0L);
    assertThat(overview.getTotalTokens()).isEqualTo(0L);
    assertThat(overview.getTotalBytes()).isEqualTo(0L);
  }

  @Test
  void findCostsByModeGroupsRowsCorrectly() {
    seedAnalysis("https://github.com/org/repo-c", "TypeScript", 10_000L, 5_000L);
    seedAnalysis("https://github.com/org/repo-d", "Go", 8_000L, 4_000L);

    List<CostByModeProjection> costs = leaderboardRepo.findCostsByMode(null, null, null);

    // seeded 1 cost per mode per analysis (3 modes × 2 analyses = 6 rows; grouped by mode = 3)
    assertThat(costs).hasSize(3);
    assertThat(costs)
        .extracting(CostByModeProjection::getMode)
        .containsExactlyInAnyOrder("AGENTIC", "ASSISTED", "RAW");
    CostByModeProjection rawRow =
        costs.stream().filter(c -> "RAW".equals(c.getMode())).findFirst().orElseThrow();
    assertThat(rawRow.getAnalysisCount()).isEqualTo(2L);
    assertThat(rawRow.getTotalCost()).isGreaterThan(BigDecimal.ZERO);
  }

  @Test
  void findCostsByModeReturnsEmptyListForEmptyDataset() {
    List<CostByModeProjection> costs = leaderboardRepo.findCostsByMode(null, null, null);
    assertThat(costs).isEmpty();
  }

  @Test
  void findCostsByModeFilteredByModeReturnsOnlyMatchingMode() {
    seedAnalysis("https://github.com/org/repo-e", "Java", 5_000L, 2_000L);

    List<CostByModeProjection> rawOnly = leaderboardRepo.findCostsByMode("RAW", null, null);

    assertThat(rawOnly).hasSize(1);
    assertThat(rawOnly.getFirst().getMode()).isEqualTo("RAW");
  }

  @Test
  void findTotalLanguageTokensReturnsGlobalSum() {
    // seed 12 languages — total tokens should be sum of ALL, not just top 10
    Map<String, LanguageStatistics> langs = java.util.LinkedHashMap.newLinkedHashMap(12);
    Map<String, LanguageTokenMetrics> tokenLangs = java.util.LinkedHashMap.newLinkedHashMap(12);
    String[] names = {
      "TypeScript",
      "Java",
      "Python",
      "Go",
      "Rust",
      "Kotlin",
      "Swift",
      "Ruby",
      "C++",
      "C#",
      "PHP",
      "Scala"
    };
    long expectedTotal = 0L;
    for (int i = 0; i < names.length; i++) {
      long tokens = (names.length - i) * 1_000L;
      expectedTotal += tokens;
      langs.put(names[i], new LanguageStatistics(names[i], 1, 10, 100));
      tokenLangs.put(names[i], new LanguageTokenMetrics(names[i], 1, tokens));
    }
    persistenceService.save(
        buildResult("https://github.com/org/multi-lang-total", langs, tokenLangs, List.of()));

    long total = leaderboardRepo.findTotalLanguageTokens();

    assertThat(total).isEqualTo(expectedTotal);
    // top-10 tokens sum is less than total (long tail exists)
    List<LanguageInsightProjection> top10 = leaderboardRepo.findTopLanguages();
    long top10Sum = top10.stream().mapToLong(LanguageInsightProjection::getTotalTokens).sum();
    assertThat(top10Sum).isLessThan(total);
  }

  @Test
  void findTopLanguagesReturnsAtMost10OrderedByTokensDesc() {
    // seed 12 distinct languages in one analysis — top 10 by tokens should be returned
    Map<String, LanguageStatistics> langs = java.util.LinkedHashMap.newLinkedHashMap(12);
    Map<String, LanguageTokenMetrics> tokenLangs = java.util.LinkedHashMap.newLinkedHashMap(12);
    String[] names = {
      "TypeScript",
      "Java",
      "Python",
      "Go",
      "Rust",
      "Kotlin",
      "Swift",
      "Ruby",
      "C++",
      "C#",
      "PHP",
      "Scala"
    };
    for (int i = 0; i < names.length; i++) {
      long tokens = (names.length - i) * 1_000L; // descending: TypeScript has most
      langs.put(names[i], new LanguageStatistics(names[i], 1, 10, 100));
      tokenLangs.put(names[i], new LanguageTokenMetrics(names[i], 1, tokens));
    }
    persistenceService.save(
        buildResult("https://github.com/org/multi-lang", langs, tokenLangs, List.of()));

    List<LanguageInsightProjection> top = leaderboardRepo.findTopLanguages();

    assertThat(top).hasSize(10);
    // first entry should be TypeScript (highest tokens = 12_000)
    assertThat(top.getFirst().getLanguageName()).isEqualTo("TypeScript");
    assertThat(top.getFirst().getTotalTokens()).isEqualTo(12_000L);
    // result is ordered descending
    for (int i = 1; i < top.size(); i++) {
      assertThat(top.get(i).getTotalTokens()).isLessThanOrEqualTo(top.get(i - 1).getTotalTokens());
    }
  }

  @Test
  void findTopLanguagesReturnsEmptyListForEmptyDataset() {
    List<LanguageInsightProjection> top = leaderboardRepo.findTopLanguages();
    assertThat(top).isEmpty();
  }

  // --- seeding helpers ---

  private void seedAnalysis(String repoUrl, String language, long tokens, long bytes) {
    Map<String, LanguageStatistics> langs =
        Map.of(language, new LanguageStatistics(language, 1, 10, bytes));
    Map<String, LanguageTokenMetrics> tokenLangs =
        Map.of(language, new LanguageTokenMetrics(language, 1, tokens));

    List<ModelCostEstimate> costs =
        List.of(
            cost(CostEstimationMode.RAW, new BigDecimal("0.001")),
            cost(CostEstimationMode.ASSISTED, new BigDecimal("0.005")),
            cost(CostEstimationMode.AGENTIC, new BigDecimal("0.020")));

    persistenceService.save(buildResult(repoUrl, langs, tokenLangs, costs));
  }

  private static RepositoryAnalysisResult buildResult(
      String repoUrl,
      Map<String, LanguageStatistics> langs,
      Map<String, LanguageTokenMetrics> tokenLangs,
      List<ModelCostEstimate> costs) {
    long totalTokens = tokenLangs.values().stream().mapToLong(LanguageTokenMetrics::tokens).sum();
    long totalBytes = langs.values().stream().mapToLong(LanguageStatistics::bytes).sum();
    return new RepositoryAnalysisResult(
        null,
        Instant.now(),
        repoUrl,
        repoUrl + ".git",
        "org",
        repoUrl.substring(repoUrl.lastIndexOf('/') + 1),
        new RepositoryScanResult(langs.size(), 10, totalBytes, List.of(), langs),
        new RepositoryTokenizationResult(
            "o200k_base",
            langs.size(),
            totalTokens,
            Map.of("openai/o200k_base", totalTokens),
            List.of(),
            tokenLangs),
        costs);
  }

  private static ModelCostEstimate cost(CostEstimationMode mode, BigDecimal totalCost) {
    return new ModelCostEstimate(
        AiProvider.OPENAI,
        "gpt-4o",
        mode,
        null,
        null,
        1000,
        0,
        1000,
        BigDecimal.ZERO,
        totalCost,
        totalCost,
        "test");
  }
}
