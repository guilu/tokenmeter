package dev.diegobarrioh.tokenmeter.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnalysisJobQueueStateTest {

  @Test
  void acceptsValidValues() {
    AnalysisJobQueueState state = new AnalysisJobQueueState(1, 3, 2);

    assertThat(state.runningCount()).isEqualTo(1);
    assertThat(state.maxConcurrency()).isEqualTo(3);
    assertThat(state.queuePosition()).isEqualTo(2);
  }

  @Test
  void acceptsNullQueuePosition() {
    AnalysisJobQueueState state = new AnalysisJobQueueState(0, 1, null);

    assertThat(state.queuePosition()).isNull();
  }

  @Test
  void rejectsNegativeRunningCount() {
    assertThatThrownBy(() -> new AnalysisJobQueueState(-1, 3, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("runningCount");
  }

  @Test
  void rejectsZeroMaxConcurrency() {
    assertThatThrownBy(() -> new AnalysisJobQueueState(0, 0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxConcurrency");
  }

  @Test
  void rejectsNegativeMaxConcurrency() {
    assertThatThrownBy(() -> new AnalysisJobQueueState(0, -2, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxConcurrency");
  }

  @Test
  void rejectsZeroQueuePosition() {
    assertThatThrownBy(() -> new AnalysisJobQueueState(0, 1, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("queuePosition");
  }

  @Test
  void rejectsNegativeQueuePosition() {
    assertThatThrownBy(() -> new AnalysisJobQueueState(0, 1, -5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("queuePosition");
  }
}
