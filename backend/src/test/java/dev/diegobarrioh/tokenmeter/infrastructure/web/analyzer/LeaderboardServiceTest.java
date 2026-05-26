package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LeaderboardJpaRepository;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LeaderboardRow;
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
  @Mock private LeaderboardJpaRepository leaderboardRepository;

  @Test
  void ranksMostExpensiveInOrderReturnedByRepository() {
    LeaderboardService service = new LeaderboardService(leaderboardRepository);
    when(leaderboardRepository.countCostFiltered(any(), any(), any())).thenReturn(2L);
    when(leaderboardRepository.findMostExpensive(any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(
            List.of(
                row("large", new BigDecimal("5.00"), 20_000),
                row("small", new BigDecimal("1.00"), 10_000)));

    LeaderboardPageResponse response =
        service.getLeaderboard(LeaderboardCategory.MOST_EXPENSIVE, 0, 10, "raw", "openai", null);

    assertThat(response.entries())
        .extracting(LeaderboardEntryResponse::name)
        .containsExactly("large", "small");
    assertThat(response.entries().getFirst().totalCost()).isEqualByComparingTo("5.00");
  }

  @Test
  void mostAnalyzedUsesDistinctRepositoryCount() {
    LeaderboardService service = new LeaderboardService(leaderboardRepository);
    when(leaderboardRepository.countDistinctRepositories()).thenReturn(2L);
    when(leaderboardRepository.findMostAnalyzed(anyInt(), anyLong()))
        .thenReturn(
            List.of(
                rowWithCount("repeated", new BigDecimal("1.00"), 10_000, 2),
                rowWithCount("other", new BigDecimal("0.50"), 5_000, 1)));

    LeaderboardPageResponse response =
        service.getLeaderboard(LeaderboardCategory.MOST_ANALYZED, 0, 10, null, null, null);

    assertThat(response.entries())
        .extracting(LeaderboardEntryResponse::name)
        .containsExactly("repeated", "other");
    assertThat(response.entries().getFirst().analysisCount()).isEqualTo(2);
    assertThat(response.totalElements()).isEqualTo(2L);
  }

  @Test
  void exposesPricingSnapshotMetadataWhenPresent() {
    LeaderboardService service = new LeaderboardService(leaderboardRepository);
    when(leaderboardRepository.countCostFiltered(any(), any(), any())).thenReturn(1L);
    when(leaderboardRepository.findMostExpensive(any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(List.of(row("repo", new BigDecimal("1.00"), 10_000)));

    LeaderboardPageResponse response =
        service.getLeaderboard(LeaderboardCategory.MOST_EXPENSIVE, 0, 10, "raw", "openai", null);

    PricingMetadata pricing = response.entries().getFirst().pricing();
    assertThat(pricing).isNotNull();
    assertThat(pricing.snapshotId()).isEqualTo(SNAPSHOT_ID);
    assertThat(pricing.primarySource()).isEqualTo("REMOTE");
    assertThat(pricing.capturedAt()).isEqualTo(SNAPSHOT_CAPTURED_AT);
  }

  @Test
  void omitsPricingForLegacyRowsWithNullSnapshotId() {
    LeaderboardService service = new LeaderboardService(leaderboardRepository);
    when(leaderboardRepository.countCostFiltered(any(), any(), any())).thenReturn(1L);
    when(leaderboardRepository.findMostExpensive(any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(List.of(legacyRow("legacy", new BigDecimal("1.00"), 10_000)));

    LeaderboardPageResponse response =
        service.getLeaderboard(LeaderboardCategory.MOST_EXPENSIVE, 0, 10, "raw", "openai", null);

    assertThat(response.entries().getFirst().pricing()).isNull();
  }

  @Test
  void invalidModeFilterIsIgnored() {
    LeaderboardService service = new LeaderboardService(leaderboardRepository);
    when(leaderboardRepository.countDistinctRepositories()).thenReturn(1L);
    when(leaderboardRepository.findLargest(any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(List.of(row("repo", BigDecimal.ONE, 50_000)));

    LeaderboardPageResponse response =
        service.getLeaderboard(LeaderboardCategory.LARGEST, 0, 10, "not_a_valid_mode", null, null);

    assertThat(response.filters()).doesNotContainKey("mode");
  }

  private static LeaderboardRow row(String name, BigDecimal totalCost, long totalTokens) {
    return rowWithCount(name, totalCost, totalTokens, 1);
  }

  private static LeaderboardRow legacyRow(String name, BigDecimal totalCost, long totalTokens) {
    LeaderboardRow delegate = rowWithCount(name, totalCost, totalTokens, 1);
    return new LeaderboardRow() {
      public UUID getId() {
        return delegate.getId();
      }

      public String getRepositoryUrl() {
        return delegate.getRepositoryUrl();
      }

      public String getOwnerName() {
        return delegate.getOwnerName();
      }

      public String getRepositoryName() {
        return delegate.getRepositoryName();
      }

      public Instant getCreatedAt() {
        return delegate.getCreatedAt();
      }

      public long getTotalFiles() {
        return delegate.getTotalFiles();
      }

      public long getTotalLines() {
        return delegate.getTotalLines();
      }

      public long getTotalBytes() {
        return delegate.getTotalBytes();
      }

      public long getTotalTokens() {
        return delegate.getTotalTokens();
      }

      public long getAnalysisCount() {
        return delegate.getAnalysisCount();
      }

      public String getProvider() {
        return delegate.getProvider();
      }

      public String getModel() {
        return delegate.getModel();
      }

      public String getMode() {
        return delegate.getMode();
      }

      public BigDecimal getTotalCost() {
        return delegate.getTotalCost();
      }

      public String getPricingSnapshotId() {
        return null;
      }

      public String getPricingPrimarySource() {
        return null;
      }

      public Instant getPricingCapturedAt() {
        return null;
      }
    };
  }

  private static final String SNAPSHOT_ID = "v1:" + "0".repeat(64);
  private static final Instant SNAPSHOT_CAPTURED_AT = Instant.parse("2026-05-24T18:42:11Z");

  private static LeaderboardRow rowWithCount(
      String name, BigDecimal totalCost, long totalTokens, long analysisCount) {
    return new LeaderboardRow() {
      public UUID getId() {
        return UUID.randomUUID();
      }

      public String getRepositoryUrl() {
        return "https://github.com/acme/" + name;
      }

      public String getOwnerName() {
        return "acme";
      }

      public String getRepositoryName() {
        return name;
      }

      public Instant getCreatedAt() {
        return Instant.now();
      }

      public long getTotalFiles() {
        return 10;
      }

      public long getTotalLines() {
        return 100;
      }

      public long getTotalBytes() {
        return totalTokens * 2;
      }

      public long getTotalTokens() {
        return totalTokens;
      }

      public long getAnalysisCount() {
        return analysisCount;
      }

      public String getProvider() {
        return "OPENAI";
      }

      public String getModel() {
        return "gpt-4o";
      }

      public String getMode() {
        return "RAW";
      }

      public BigDecimal getTotalCost() {
        return totalCost;
      }

      public String getPricingSnapshotId() {
        return SNAPSHOT_ID;
      }

      public String getPricingPrimarySource() {
        return "REMOTE";
      }

      public Instant getPricingCapturedAt() {
        return SNAPSHOT_CAPTURED_AT;
      }
    };
  }
}
