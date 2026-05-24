package dev.diegobarrioh.tokenmeter.application.analyzer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobErrorCode;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration coverage for the concurrent-analysis-limits cap and queue ceiling. Each test stubs
 * {@link AnalysisJobExecutionService#runJob(AnalysisJobId)} so the pipeline pauses on a {@link
 * CountDownLatch}: this keeps slots and queue rows in the desired state long enough for Awaitility
 * to assert invariants without touching git, the file system or H2 at pipeline depth.
 *
 * <p>{@link DirtiesContext} forces a fresh context (and therefore a fresh executor pool and a fresh
 * H2 in-memory schema) per test method to avoid leaking blocked worker threads or stale {@code
 * analysis_job} rows from one test into the next.
 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "tokenmeter.analyze-throttle.max-concurrent=2",
      "tokenmeter.analyze-throttle.queue-capacity=10",
      "tokenmeter.analyze-throttle.rate-limit-requests-per-window=1000"
    })
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AnalysisJobExecutionServiceConcurrencyIT {

  @Autowired private AnalysisJobSubmissionService submissionService;
  @Autowired private AnalysisJobRepository repository;
  @Autowired private AnalysisJobProgressEmitter emitter;
  @MockitoBean private AnalysisJobExecutionService executionService;

  private final List<CountDownLatch> latches = new ArrayList<>();

  @AfterEach
  void releaseLatchesAndCleanup() {
    latches.forEach(
        latch -> {
          while (latch.getCount() > 0) {
            latch.countDown();
          }
        });
    forceTerminalAll();
  }

  @Test
  void runningCountNeverExceedsMaxConcurrency() throws Exception {
    int maxConcurrent = 2;
    int totalJobs = 5;
    CountDownLatch releaseAll = newLatch(1);
    ConcurrentHashMap<AnalysisJobId, Boolean> reachedRunning = new ConcurrentHashMap<>();
    AtomicInteger overshoot = new AtomicInteger();

    doAnswer(
            invocation -> {
              AnalysisJobId id = invocation.getArgument(0);
              emitter.transition(id, AnalysisJobPhase.CHECKING_CACHE, 5, "running");
              reachedRunning.put(id, Boolean.TRUE);
              int running = repository.countByStatus(AnalysisJobStatus.RUNNING);
              if (running > maxConcurrent) {
                overshoot.incrementAndGet();
              }
              releaseAll.await(10, SECONDS);
              emitter.fail(id, AnalysisJobErrorCode.ANALYSIS_FAILED, "test done");
              return null;
            })
        .when(executionService)
        .runJob(org.mockito.ArgumentMatchers.any());

    for (int i = 0; i < totalJobs; i++) {
      submissionService.submit("https://github.com/foo/repo-" + i);
    }

    await()
        .atMost(5, SECONDS)
        .pollInterval(50, MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(repository.countByStatus(AnalysisJobStatus.RUNNING))
                    .isEqualTo(maxConcurrent));

    // Hold for several polls so any over-promotion would surface.
    for (int i = 0; i < 10; i++) {
      assertThat(repository.countByStatus(AnalysisJobStatus.RUNNING))
          .isLessThanOrEqualTo(maxConcurrent);
      Thread.sleep(20);
    }

    int queued = repository.countByStatus(AnalysisJobStatus.QUEUED);
    assertThat(queued).isEqualTo(totalJobs - maxConcurrent);

    assertThat(overshoot.get())
        .as("countByStatus(RUNNING) must NEVER exceed maxConcurrent")
        .isZero();
  }

  @Test
  void failedJobReleasesSlotForNextQueued() throws Exception {
    int maxConcurrent = 2;
    CountDownLatch releaseSurvivors = newLatch(1);
    List<AnalysisJobId> idsToFail = new ArrayList<>();

    doAnswer(
            invocation -> {
              AnalysisJobId id = invocation.getArgument(0);
              emitter.transition(id, AnalysisJobPhase.CHECKING_CACHE, 5, "running");
              synchronized (idsToFail) {
                if (idsToFail.contains(id)) {
                  emitter.fail(id, AnalysisJobErrorCode.ANALYSIS_FAILED, "boom");
                  return null;
                }
              }
              releaseSurvivors.await(10, SECONDS);
              emitter.fail(id, AnalysisJobErrorCode.ANALYSIS_FAILED, "test done");
              return null;
            })
        .when(executionService)
        .runJob(org.mockito.ArgumentMatchers.any());

    AnalysisJobSnapshot first = submissionService.submit("https://github.com/foo/will-fail");
    synchronized (idsToFail) {
      idsToFail.add(first.id());
    }

    await()
        .atMost(5, SECONDS)
        .pollInterval(50, MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(repository.findById(first.id()).orElseThrow().status())
                    .isEqualTo(AnalysisJobStatus.FAILED));

    submissionService.submit("https://github.com/foo/survivor-1");
    submissionService.submit("https://github.com/foo/survivor-2");
    submissionService.submit("https://github.com/foo/survivor-3");

    await()
        .atMost(5, SECONDS)
        .pollInterval(50, MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(repository.countByStatus(AnalysisJobStatus.RUNNING))
                    .isEqualTo(maxConcurrent));

    // The cap must hold steady even after the failure released a slot.
    for (int i = 0; i < 10; i++) {
      assertThat(repository.countByStatus(AnalysisJobStatus.RUNNING))
          .isLessThanOrEqualTo(maxConcurrent);
      Thread.sleep(20);
    }
    assertThat(repository.countByStatus(AnalysisJobStatus.QUEUED)).isEqualTo(1);
  }

  @Test
  void postWhenQueueCeilingReachedReturns429() throws Exception {
    int maxConcurrent = 2;
    int queueCapacity = 10;
    CountDownLatch holdEverything = newLatch(1);

    doAnswer(
            invocation -> {
              AnalysisJobId id = invocation.getArgument(0);
              emitter.transition(id, AnalysisJobPhase.CHECKING_CACHE, 5, "running");
              holdEverything.await(10, SECONDS);
              emitter.fail(id, AnalysisJobErrorCode.ANALYSIS_FAILED, "test done");
              return null;
            })
        .when(executionService)
        .runJob(org.mockito.ArgumentMatchers.any());

    int acceptable = maxConcurrent + queueCapacity; // 12
    for (int i = 0; i < acceptable; i++) {
      submissionService.submit("https://github.com/foo/sat-" + i);
    }

    await()
        .atMost(5, SECONDS)
        .pollInterval(50, MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(repository.countByStatus(AnalysisJobStatus.RUNNING))
                    .isEqualTo(maxConcurrent));

    long countBefore = totalRowCount();

    assertThatThrownBy(() -> submissionService.submit("https://github.com/foo/over-the-ceiling"))
        .isInstanceOf(RepositoryIntakeException.class)
        .hasMessageContaining("queue")
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.RATE_LIMITED);

    long countAfter = totalRowCount();
    assertThat(countAfter)
        .as("rejected submission MUST NOT leave a row in analysis_job")
        .isEqualTo(countBefore);
  }

  private CountDownLatch newLatch(int count) {
    CountDownLatch latch = new CountDownLatch(count);
    latches.add(latch);
    return latch;
  }

  private long totalRowCount() {
    return (long) repository.countByStatus(AnalysisJobStatus.QUEUED)
        + repository.countByStatus(AnalysisJobStatus.RUNNING)
        + repository.countByStatus(AnalysisJobStatus.SUCCESS)
        + repository.countByStatus(AnalysisJobStatus.FAILED);
  }

  private void forceTerminalAll() {
    // Best-effort cleanup so the next @DirtiesContext rebuild does not leak threads still blocked
    // on the latch. Even though @DirtiesContext closes the context, leaking the latch wait until
    // the await-termination shutdown timeout would slow the test suite down.
    Instant now = Instant.now();
    repository
        .findNonTerminal()
        .forEach(
            snapshot ->
                repository.markFailed(
                    snapshot.id(), AnalysisJobErrorCode.ANALYSIS_FAILED, "test cleanup", now));
  }
}
