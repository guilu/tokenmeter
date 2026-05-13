package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.AnalysisEntity;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.AnalysisJpaRepository;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.AnalysisStatus;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.CostEstimateEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {
  @Mock private AnalysisJpaRepository repository;

  @Test
  void ranksMostExpensiveRepositoriesUsingHighestMatchingCostEstimate() {
    LeaderboardService service = new LeaderboardService(repository);
    AnalysisEntity cheaper = analysis("https://github.com/acme/small", "acme", "small", 10_000, 10);
    cheaper.addCostEstimate(cost("gpt-4o", new BigDecimal("1.00")));
    AnalysisEntity expensive =
        analysis("https://github.com/acme/large", "acme", "large", 20_000, 20);
    expensive.addCostEstimate(cost("gpt-4o", new BigDecimal("5.00")));
    expensive.addCostEstimate(cost("cheap-model", new BigDecimal("0.10")));
    when(repository.findAll()).thenReturn(List.of(cheaper, expensive));

    LeaderboardPageResponse response =
        service.getLeaderboard(LeaderboardCategory.MOST_EXPENSIVE, 0, 10, "raw", "openai", null);

    assertThat(response.entries())
        .extracting(LeaderboardEntryResponse::name)
        .containsExactly("large", "small");
    assertThat(response.entries().getFirst().totalCost()).isEqualByComparingTo("5.00");
  }

  @Test
  void groupsMostAnalyzedRepositoriesByRepositoryUrl() {
    LeaderboardService service = new LeaderboardService(repository);
    AnalysisEntity first =
        analysis("https://github.com/acme/repeated", "acme", "repeated", 10_000, 10);
    AnalysisEntity second =
        analysis("https://github.com/acme/repeated", "acme", "repeated", 12_000, 12);
    AnalysisEntity other = analysis("https://github.com/acme/other", "acme", "other", 15_000, 15);
    when(repository.findAll()).thenReturn(List.of(first, second, other));

    LeaderboardPageResponse response =
        service.getLeaderboard(LeaderboardCategory.MOST_ANALYZED, 0, 10, null, null, null);

    assertThat(response.entries())
        .extracting(LeaderboardEntryResponse::name)
        .containsExactly("repeated", "other");
    assertThat(response.entries().getFirst().analysisCount()).isEqualTo(2);
  }

  private static AnalysisEntity analysis(
      String repositoryUrl,
      String owner,
      String name,
      long totalTokens,
      long createdOffsetSeconds) {
    return new AnalysisEntity(
        UUID.randomUUID(),
        repositoryUrl,
        repositoryUrl + ".git",
        owner,
        name,
        AnalysisStatus.SUCCESS,
        4,
        100,
        totalTokens * 2,
        "o200k_base",
        totalTokens,
        Instant.parse("2026-05-12T20:00:00Z").plusSeconds(createdOffsetSeconds));
  }

  private static CostEstimateEntity cost(String model, BigDecimal totalCost) {
    return new CostEstimateEntity(
        AiProvider.OPENAI,
        model,
        CostEstimationMode.RAW,
        1_000,
        0,
        1_000,
        BigDecimal.ZERO,
        totalCost,
        totalCost,
        "test");
  }
}
