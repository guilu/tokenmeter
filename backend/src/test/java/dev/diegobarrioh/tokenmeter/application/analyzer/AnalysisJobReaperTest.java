package dev.diegobarrioh.tokenmeter.application.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AnalysisJobReaperTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-05-23T09:00:00Z");

  @Test
  void marksEveryNonTerminalJobAsInterrupted() {
    AnalysisJobRepository repository = Mockito.mock(AnalysisJobRepository.class);
    AnalysisJobSnapshot queued = sampleSnapshot(AnalysisJobStatus.QUEUED, AnalysisJobPhase.QUEUED);
    AnalysisJobSnapshot running =
        sampleSnapshot(AnalysisJobStatus.RUNNING, AnalysisJobPhase.CLONING_REPOSITORY);
    when(repository.findNonTerminal()).thenReturn(List.of(queued, running));

    AnalysisJobReaper reaper =
        new AnalysisJobReaper(repository, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    int reconciled = reaper.reconcile();

    assertThat(reconciled).isEqualTo(2);
    verify(repository).markInterrupted(eq(queued.id()), eq(FIXED_NOW));
    verify(repository).markInterrupted(eq(running.id()), eq(FIXED_NOW));
  }

  @Test
  void doesNothingWhenNoStaleJobsAreFound() {
    AnalysisJobRepository repository = Mockito.mock(AnalysisJobRepository.class);
    when(repository.findNonTerminal()).thenReturn(List.of());

    AnalysisJobReaper reaper =
        new AnalysisJobReaper(repository, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    assertThat(reaper.reconcile()).isZero();
    verify(repository, times(1)).findNonTerminal();
    verify(repository, never()).markInterrupted(any(), any());
  }

  private static AnalysisJobSnapshot sampleSnapshot(
      AnalysisJobStatus status, AnalysisJobPhase phase) {
    return new AnalysisJobSnapshot(
        AnalysisJobId.random(),
        "https://github.com/owner/repo",
        status,
        phase,
        0,
        null,
        null,
        null,
        null,
        AnalysisJobMetrics.empty(),
        FIXED_NOW,
        null,
        FIXED_NOW,
        null);
  }
}
