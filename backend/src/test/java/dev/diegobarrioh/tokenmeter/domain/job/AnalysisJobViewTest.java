package dev.diegobarrioh.tokenmeter.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnalysisJobViewTest {

  @Test
  void requiresSnapshot() {
    assertThatThrownBy(() -> new AnalysisJobView(null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("snapshot");
  }

  @Test
  void allowsNullQueueState() {
    AnalysisJobView view = new AnalysisJobView(snapshot(AnalysisJobStatus.SUCCESS), null);

    assertThat(view.snapshot().status()).isEqualTo(AnalysisJobStatus.SUCCESS);
    assertThat(view.queueState()).isNull();
  }

  @Test
  void exposesQueueStateWhenProvided() {
    AnalysisJobQueueState queueState = new AnalysisJobQueueState(1, 2, 3);

    AnalysisJobView view = new AnalysisJobView(snapshot(AnalysisJobStatus.QUEUED), queueState);

    assertThat(view.queueState()).isSameAs(queueState);
  }

  @Test
  void equalityIsByValue() {
    AnalysisJobSnapshot snapshot = snapshot(AnalysisJobStatus.QUEUED);
    AnalysisJobQueueState state = new AnalysisJobQueueState(1, 2, 3);

    AnalysisJobView first = new AnalysisJobView(snapshot, state);
    AnalysisJobView second = new AnalysisJobView(snapshot, new AnalysisJobQueueState(1, 2, 3));

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }

  private static AnalysisJobSnapshot snapshot(AnalysisJobStatus status) {
    Instant now = Instant.parse("2026-05-23T08:00:00Z");
    AnalysisJobPhase phase =
        switch (status) {
          case QUEUED -> AnalysisJobPhase.QUEUED;
          case RUNNING -> AnalysisJobPhase.SCANNING_FILES;
          case SUCCESS -> AnalysisJobPhase.COMPLETED;
          case FAILED -> AnalysisJobPhase.FAILED;
        };
    return new AnalysisJobSnapshot(
        AnalysisJobId.random(),
        "https://github.com/foo/bar",
        status,
        phase,
        status == AnalysisJobStatus.SUCCESS ? 100 : 0,
        null,
        null,
        null,
        null,
        AnalysisJobMetrics.empty(),
        now,
        null,
        now,
        status.isTerminal() ? now : null);
  }
}
