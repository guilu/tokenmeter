package dev.diegobarrioh.tokenmeter.application.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AnalysisJobSubmissionServiceTest {

  private AnalysisJobRepository repository;
  private AnalysisJobExecutionService executionService;
  private Executor executor;
  private Clock clock;
  private AnalysisJobSubmissionService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(AnalysisJobRepository.class);
    executionService = Mockito.mock(AnalysisJobExecutionService.class);
    executor = Mockito.mock(Executor.class);
    clock = Clock.fixed(Instant.parse("2026-05-23T08:00:00Z"), ZoneOffset.UTC);
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    service = new AnalysisJobSubmissionService(repository, executionService, executor, clock);
  }

  @Test
  void rejectsInvalidUrlBeforeTouchingRepositoryOrExecutor() {
    assertThatThrownBy(() -> service.submit("not-a-url"))
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.INVALID_URL);

    verify(repository, never()).save(any());
    verify(executor, never()).execute(any());
  }

  @Test
  void persistsQueuedJobAndDispatchesRunnable() {
    AnalysisJobSnapshot persisted = service.submit("https://github.com/guilu/tokenmeter");

    assertThat(persisted.status()).isEqualTo(AnalysisJobStatus.QUEUED);
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).execute(runnableCaptor.capture());

    runnableCaptor.getValue().run();
    verify(executionService).runJob(persisted.id());
  }

  @Test
  void rejectsWith429WhenQueueCeilingReached() {
    // The executor's AbortPolicy only raises RejectedExecutionException when its internal
    // LinkedBlockingQueue is full (queue ceiling reached). Slot contention alone enqueues silently
    // after the concurrent-analysis-limits change.
    doThrow(new RejectedExecutionException("queue full")).when(executor).execute(any());

    assertThatThrownBy(() -> service.submit("https://github.com/guilu/tokenmeter"))
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.RATE_LIMITED);

    assertThatThrownBy(() -> service.submit("https://github.com/guilu/tokenmeter"))
        .isInstanceOf(RepositoryIntakeException.class)
        .hasMessageContaining("queue");

    ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
    verify(repository, Mockito.atLeastOnce()).deleteById(idCaptor.capture());
    assertThat(idCaptor.getValue()).isNotNull();
  }
}
