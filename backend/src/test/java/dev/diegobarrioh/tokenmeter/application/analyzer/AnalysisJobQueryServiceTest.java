package dev.diegobarrioh.tokenmeter.application.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobErrorCode;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobView;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalysisJobQueryServiceTest {

  private AnalysisJobRepository repository;
  private AnalyzeThrottleProperties throttleProperties;
  private AnalysisJobQueryService service;

  @BeforeEach
  void setUp() {
    repository = mock(AnalysisJobRepository.class);
    throttleProperties =
        new AnalyzeThrottleProperties(
            2,
            256,
            5,
            Duration.ofMinutes(1),
            new AnalyzeThrottleProperties.Retention(
                Duration.ofDays(7), Duration.ofDays(30), "0 30 3 * * *"));
    service = new AnalysisJobQueryService(repository, throttleProperties);
  }

  @Test
  void findByIdDelegatesToRepository() {
    AnalysisJobId id = AnalysisJobId.random();
    AnalysisJobSnapshot snapshot = snapshotFor(id, AnalysisJobStatus.QUEUED);
    when(repository.findById(id)).thenReturn(Optional.of(snapshot));

    assertThat(service.findById(id)).contains(snapshot);
  }

  @Test
  void getViewIncludesQueueStateForQueuedJob() {
    AnalysisJobId id = AnalysisJobId.random();
    AnalysisJobSnapshot snapshot = snapshotFor(id, AnalysisJobStatus.QUEUED);
    when(repository.findById(id)).thenReturn(Optional.of(snapshot));
    when(repository.countByStatus(AnalysisJobStatus.RUNNING)).thenReturn(2);
    when(repository.countQueuedAheadOf(id)).thenReturn(3);

    AnalysisJobView view = service.getView(id).orElseThrow();

    assertThat(view.snapshot()).isSameAs(snapshot);
    assertThat(view.queueState()).isNotNull();
    assertThat(view.queueState().runningCount()).isEqualTo(2);
    assertThat(view.queueState().maxConcurrency()).isEqualTo(2);
    assertThat(view.queueState().queuePosition()).isEqualTo(3);
  }

  @Test
  void getViewOmitsQueuePositionForRunningJob() {
    AnalysisJobId id = AnalysisJobId.random();
    AnalysisJobSnapshot snapshot = snapshotFor(id, AnalysisJobStatus.RUNNING);
    when(repository.findById(id)).thenReturn(Optional.of(snapshot));
    when(repository.countByStatus(AnalysisJobStatus.RUNNING)).thenReturn(1);

    AnalysisJobView view = service.getView(id).orElseThrow();

    assertThat(view.queueState()).isNotNull();
    assertThat(view.queueState().runningCount()).isEqualTo(1);
    assertThat(view.queueState().maxConcurrency()).isEqualTo(2);
    assertThat(view.queueState().queuePosition()).isNull();
    verify(repository, never()).countQueuedAheadOf(any());
  }

  @Test
  void getViewOmitsQueueStateForSuccessfulJob() {
    AnalysisJobId id = AnalysisJobId.random();
    AnalysisJobSnapshot snapshot = snapshotFor(id, AnalysisJobStatus.SUCCESS);
    when(repository.findById(id)).thenReturn(Optional.of(snapshot));

    AnalysisJobView view = service.getView(id).orElseThrow();

    assertThat(view.queueState()).isNull();
    verify(repository, never()).countByStatus(any());
    verify(repository, never()).countQueuedAheadOf(any());
  }

  @Test
  void getViewOmitsQueueStateForFailedJob() {
    AnalysisJobId id = AnalysisJobId.random();
    AnalysisJobSnapshot snapshot = snapshotFor(id, AnalysisJobStatus.FAILED);
    when(repository.findById(id)).thenReturn(Optional.of(snapshot));

    AnalysisJobView view = service.getView(id).orElseThrow();

    assertThat(view.queueState()).isNull();
    verify(repository, never()).countByStatus(any());
    verify(repository, never()).countQueuedAheadOf(any());
  }

  @Test
  void getViewReturnsEmptyWhenSnapshotMissing() {
    AnalysisJobId id = AnalysisJobId.random();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThat(service.getView(id)).isEmpty();
  }

  @Test
  void getViewTreatsZeroQueuePositionAsNullToAvoidInvariantViolation() {
    AnalysisJobId id = AnalysisJobId.random();
    AnalysisJobSnapshot snapshot = snapshotFor(id, AnalysisJobStatus.QUEUED);
    when(repository.findById(id)).thenReturn(Optional.of(snapshot));
    when(repository.countByStatus(AnalysisJobStatus.RUNNING)).thenReturn(0);
    // Race: snapshot says QUEUED but repository already promoted the row → returns 0.
    when(repository.countQueuedAheadOf(id)).thenReturn(0);

    AnalysisJobView view = service.getView(id).orElseThrow();

    assertThat(view.queueState()).isNotNull();
    assertThat(view.queueState().queuePosition()).isNull();
  }

  private static AnalysisJobSnapshot snapshotFor(AnalysisJobId id, AnalysisJobStatus status) {
    Instant now = Instant.parse("2026-05-23T08:00:00Z");
    AnalysisJobPhase phase =
        switch (status) {
          case QUEUED -> AnalysisJobPhase.QUEUED;
          case RUNNING -> AnalysisJobPhase.SCANNING_FILES;
          case SUCCESS -> AnalysisJobPhase.COMPLETED;
          case FAILED -> AnalysisJobPhase.FAILED;
        };
    int progress = status == AnalysisJobStatus.SUCCESS ? 100 : 0;
    UUID analysisId = status == AnalysisJobStatus.SUCCESS ? UUID.randomUUID() : null;
    AnalysisJobErrorCode errorCode =
        status == AnalysisJobStatus.FAILED ? AnalysisJobErrorCode.ANALYSIS_FAILED : null;
    String errorMessage = status == AnalysisJobStatus.FAILED ? "boom" : null;
    return new AnalysisJobSnapshot(
        id,
        "https://github.com/foo/bar",
        status,
        phase,
        progress,
        null,
        analysisId,
        errorCode,
        errorMessage,
        AnalysisJobMetrics.empty(),
        now,
        status == AnalysisJobStatus.RUNNING || status.isTerminal() ? now : null,
        now,
        status.isTerminal() ? now : null);
  }
}
