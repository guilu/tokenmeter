package dev.diegobarrioh.tokenmeter.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class AnalysisJobMetricsTest {

  @Test
  void acceptsAllNulls() {
    AnalysisJobMetrics metrics = new AnalysisJobMetrics(null, null, null, null, null, null);
    assertThat(metrics.filesDiscovered()).isNull();
    assertThat(metrics.tokensCounted()).isNull();
    assertThat(metrics.pricingModelsProcessed()).isNull();
  }

  @Test
  void acceptsZeroAndPositiveValues() {
    AnalysisJobMetrics metrics = new AnalysisJobMetrics(0L, 10L, 1L, 1234L, 0, 3);
    assertThat(metrics.filesDiscovered()).isZero();
    assertThat(metrics.filesProcessed()).isEqualTo(10L);
    assertThat(metrics.contextWindows()).isZero();
  }

  @Test
  void rejectsNegativeFilesDiscovered() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AnalysisJobMetrics(-1L, null, null, null, null, null))
        .withMessageContaining("filesDiscovered");
  }

  @Test
  void rejectsNegativeTokensCounted() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AnalysisJobMetrics(null, null, null, -7L, null, null))
        .withMessageContaining("tokensCounted");
  }

  @Test
  void rejectsNegativeContextWindows() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AnalysisJobMetrics(null, null, null, null, -1, null))
        .withMessageContaining("contextWindows");
  }

  @Test
  void rejectsNegativePricingModelsProcessed() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AnalysisJobMetrics(null, null, null, null, null, -2))
        .withMessageContaining("pricingModelsProcessed");
  }

  @Test
  void emptyIsAllNulls() {
    assertThat(AnalysisJobMetrics.empty())
        .extracting(
            AnalysisJobMetrics::filesDiscovered,
            AnalysisJobMetrics::filesProcessed,
            AnalysisJobMetrics::filesSkipped,
            AnalysisJobMetrics::tokensCounted,
            AnalysisJobMetrics::contextWindows,
            AnalysisJobMetrics::pricingModelsProcessed)
        .containsOnlyNulls();
  }
}
