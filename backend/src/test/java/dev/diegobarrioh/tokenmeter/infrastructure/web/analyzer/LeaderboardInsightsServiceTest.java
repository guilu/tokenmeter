package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.CostByModeProjection;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LanguageInsightProjection;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LeaderboardJpaRepository;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LeaderboardOverviewProjection;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardInsightsServiceTest {

  @Mock private LeaderboardJpaRepository leaderboardRepository;

  @Test
  void getOverviewReturnsAggregatesFromRepository() {
    LeaderboardInsightsService service = new LeaderboardInsightsService(leaderboardRepository);
    when(leaderboardRepository.findOverview(isNull(), isNull(), isNull()))
        .thenReturn(overviewProjection(5L, 10L, 100_000L, 500_000L));
    when(leaderboardRepository.findCostsByMode(isNull(), isNull(), isNull()))
        .thenReturn(
            List.of(
                costByModeProjection("RAW", new BigDecimal("12.50"), 10L),
                costByModeProjection("ASSISTED", new BigDecimal("62.50"), 10L)));

    LeaderboardOverviewResponse response = service.getOverview(null, null, null);

    assertThat(response.totalRepos()).isEqualTo(5L);
    assertThat(response.totalAnalyses()).isEqualTo(10L);
    assertThat(response.totalTokens()).isEqualTo(100_000L);
    assertThat(response.totalBytes()).isEqualTo(500_000L);
    assertThat(response.costsByMode()).hasSize(2);
    assertThat(response.costsByMode().getFirst().mode()).isEqualTo("raw");
    assertThat(response.costsByMode().getFirst().totalCost())
        .isEqualByComparingTo(new BigDecimal("12.50"));
    assertThat(response.costsByMode().getFirst().analysisCount()).isEqualTo(10L);
  }

  @Test
  void getOverviewReturnsZerosForEmptyDataset() {
    LeaderboardInsightsService service = new LeaderboardInsightsService(leaderboardRepository);
    when(leaderboardRepository.findOverview(any(), any(), any()))
        .thenReturn(overviewProjection(0L, 0L, 0L, 0L));
    when(leaderboardRepository.findCostsByMode(any(), any(), any())).thenReturn(List.of());

    LeaderboardOverviewResponse response = service.getOverview(null, null, null);

    assertThat(response.totalRepos()).isEqualTo(0L);
    assertThat(response.totalAnalyses()).isEqualTo(0L);
    assertThat(response.totalTokens()).isEqualTo(0L);
    assertThat(response.totalBytes()).isEqualTo(0L);
    assertThat(response.costsByMode()).isEmpty();
  }

  @Test
  void getOverviewNormalizesAndPropagatesFiltersToRepository() {
    LeaderboardInsightsService service = new LeaderboardInsightsService(leaderboardRepository);
    when(leaderboardRepository.findOverview("RAW", "OPENAI", "gpt-4o"))
        .thenReturn(overviewProjection(2L, 4L, 50_000L, 200_000L));
    when(leaderboardRepository.findCostsByMode("RAW", "OPENAI", "gpt-4o")).thenReturn(List.of());

    service.getOverview("raw", "openai", "gpt-4o");

    verify(leaderboardRepository).findOverview("RAW", "OPENAI", "gpt-4o");
    verify(leaderboardRepository).findCostsByMode("RAW", "OPENAI", "gpt-4o");
  }

  @Test
  void getOverviewIgnoresInvalidModeFilter() {
    LeaderboardInsightsService service = new LeaderboardInsightsService(leaderboardRepository);
    when(leaderboardRepository.findOverview(isNull(), isNull(), isNull()))
        .thenReturn(overviewProjection(1L, 1L, 1L, 1L));
    when(leaderboardRepository.findCostsByMode(isNull(), isNull(), isNull())).thenReturn(List.of());

    service.getOverview("INVALID_MODE", null, null);

    verify(leaderboardRepository).findOverview(null, null, null);
  }

  @Test
  void getOverviewForwardsModeToFindCostsByMode() {
    LeaderboardInsightsService service = new LeaderboardInsightsService(leaderboardRepository);
    when(leaderboardRepository.findOverview("RAW", null, null))
        .thenReturn(overviewProjection(1L, 2L, 1_000L, 5_000L));
    when(leaderboardRepository.findCostsByMode("RAW", null, null))
        .thenReturn(List.of(costByModeProjection("RAW", new BigDecimal("0.50"), 2L)));

    LeaderboardOverviewResponse response = service.getOverview("raw", null, null);

    verify(leaderboardRepository).findCostsByMode("RAW", null, null);
    assertThat(response.costsByMode()).hasSize(1);
    assertThat(response.costsByMode().getFirst().mode()).isEqualTo("raw");
  }

  @Test
  void getLanguagesForwardsFiltersToResponseAndDoesNotAlterDataset() {
    LeaderboardInsightsService service = new LeaderboardInsightsService(leaderboardRepository);
    when(leaderboardRepository.findTopLanguages())
        .thenReturn(List.of(languageProjection("Java", 7_000L, 3L)));
    when(leaderboardRepository.findTotalLanguageTokens()).thenReturn(10_000L);

    LeaderboardLanguagesResponse response = service.getLanguages("raw", "openai", "gpt-4o");

    // dataset unchanged — still calls findTopLanguages() without filtering
    verify(leaderboardRepository).findTopLanguages();
    // filters are echoed in the response
    assertThat(response.filters()).isNotNull();
    assertThat(response.filters()).containsEntry("mode", "raw");
    assertThat(response.filters()).containsEntry("provider", "openai");
    assertThat(response.filters()).containsEntry("model", "gpt-4o");
  }

  @Test
  void getLanguagesWithNoFiltersReturnsNullFilters() {
    LeaderboardInsightsService service = new LeaderboardInsightsService(leaderboardRepository);
    when(leaderboardRepository.findTopLanguages()).thenReturn(List.of());
    when(leaderboardRepository.findTotalLanguageTokens()).thenReturn(0L);

    LeaderboardLanguagesResponse response = service.getLanguages(null, null, null);

    assertThat(response.filters()).isNull();
  }

  @Test
  void getLanguagesUsesGlobalTokensAsDenominator() {
    LeaderboardInsightsService service = new LeaderboardInsightsService(leaderboardRepository);
    // Top 2 returned by findTopLanguages, but global total is 20_000 (long tail of 10_000
    // elsewhere)
    when(leaderboardRepository.findTopLanguages())
        .thenReturn(
            List.of(
                languageProjection("Java", 7_000L, 3L), languageProjection("Python", 3_000L, 2L)));
    when(leaderboardRepository.findTotalLanguageTokens()).thenReturn(20_000L);

    LeaderboardLanguagesResponse response = service.getLanguages(null, null, null);

    // sharePercent must use 20_000 as denominator, NOT 10_000 (sum of top-2)
    assertThat(response.totalTokensAllLanguages()).isEqualTo(20_000L);
    assertThat(response.languages().getFirst().sharePercent())
        .isEqualByComparingTo(new BigDecimal("35.00")); // 7_000 / 20_000 * 100
    assertThat(response.languages().get(1).sharePercent())
        .isEqualByComparingTo(new BigDecimal("15.00")); // 3_000 / 20_000 * 100
    // top-2 shares do NOT sum to 100% because there is a long tail
    BigDecimal sum =
        response.languages().stream()
            .map(LanguageInsightEntry::sharePercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(sum).isLessThan(new BigDecimal("100.00"));
  }

  @Test
  void getLanguagesComputesSharePercentCorrectly() {
    LeaderboardInsightsService service = new LeaderboardInsightsService(leaderboardRepository);
    when(leaderboardRepository.findTopLanguages())
        .thenReturn(
            List.of(
                languageProjection("Java", 7_000L, 3L), languageProjection("Python", 3_000L, 2L)));
    when(leaderboardRepository.findTotalLanguageTokens()).thenReturn(10_000L);

    LeaderboardLanguagesResponse response = service.getLanguages(null, null, null);

    assertThat(response.languages()).hasSize(2);
    assertThat(response.totalTokensAllLanguages()).isEqualTo(10_000L);
    assertThat(response.languages().getFirst().language()).isEqualTo("Java");
    assertThat(response.languages().getFirst().totalTokens()).isEqualTo(7_000L);
    assertThat(response.languages().getFirst().repoCount()).isEqualTo(3L);
    assertThat(response.languages().getFirst().sharePercent())
        .isEqualByComparingTo(new BigDecimal("70.00"));
    assertThat(response.languages().get(1).sharePercent())
        .isEqualByComparingTo(new BigDecimal("30.00"));
  }

  @Test
  void getLanguagesGuardsAgainstZeroDenominator() {
    LeaderboardInsightsService service = new LeaderboardInsightsService(leaderboardRepository);
    when(leaderboardRepository.findTopLanguages()).thenReturn(List.of());
    when(leaderboardRepository.findTotalLanguageTokens()).thenReturn(0L);

    LeaderboardLanguagesResponse response = service.getLanguages(null, null, null);

    assertThat(response.languages()).isEmpty();
    assertThat(response.totalTokensAllLanguages()).isEqualTo(0L);
  }

  // --- Projections (hand-rolled, no Mockito) ---

  private static LeaderboardOverviewProjection overviewProjection(
      long totalRepos, long totalAnalyses, long totalTokens, long totalBytes) {
    return new LeaderboardOverviewProjection() {
      public long getTotalRepos() {
        return totalRepos;
      }

      public long getTotalAnalyses() {
        return totalAnalyses;
      }

      public long getTotalTokens() {
        return totalTokens;
      }

      public long getTotalBytes() {
        return totalBytes;
      }
    };
  }

  private static CostByModeProjection costByModeProjection(
      String mode, BigDecimal totalCost, long analysisCount) {
    return new CostByModeProjection() {
      public String getMode() {
        return mode;
      }

      public BigDecimal getTotalCost() {
        return totalCost;
      }

      public long getAnalysisCount() {
        return analysisCount;
      }
    };
  }

  private static LanguageInsightProjection languageProjection(
      String languageName, long totalTokens, long repoCount) {
    return new LanguageInsightProjection() {
      public String getLanguageName() {
        return languageName;
      }

      public long getTotalTokens() {
        return totalTokens;
      }

      public long getRepoCount() {
        return repoCount;
      }
    };
  }
}
