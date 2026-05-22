package dev.diegobarrioh.tokenmeter.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnalysisJobStatusTest {

  @Test
  void queuedAndRunningAreNonTerminal() {
    assertThat(AnalysisJobStatus.QUEUED.isTerminal()).isFalse();
    assertThat(AnalysisJobStatus.RUNNING.isTerminal()).isFalse();
  }

  @Test
  void successAndFailedAreTerminal() {
    assertThat(AnalysisJobStatus.SUCCESS.isTerminal()).isTrue();
    assertThat(AnalysisJobStatus.FAILED.isTerminal()).isTrue();
  }

  @Test
  void closedSetMatchesSpec() {
    assertThat(AnalysisJobStatus.values())
        .containsExactly(
            AnalysisJobStatus.QUEUED,
            AnalysisJobStatus.RUNNING,
            AnalysisJobStatus.SUCCESS,
            AnalysisJobStatus.FAILED);
  }
}
