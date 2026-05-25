package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingSnapshotIdentityService;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import dev.diegobarrioh.tokenmeter.domain.repository.GitHubRepositoryUrl;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Synchronous front door for {@code POST /api/analyze}. Validates the URL, persists an initial
 * QUEUED snapshot and hands the job off to the dedicated executor. Returns the persisted snapshot
 * so the controller can build the 202 response.
 */
@Service
public class AnalysisJobSubmissionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisJobSubmissionService.class);

  private final AnalysisJobRepository jobRepository;
  private final AnalysisJobExecutionService executionService;
  private final Executor executor;
  private final Clock clock;
  private final PricingSnapshotIdentityService pricingIdentityService;
  private final AnalysisPersistenceService analysisPersistenceService;

  public AnalysisJobSubmissionService(
      AnalysisJobRepository jobRepository,
      AnalysisJobExecutionService executionService,
      @Qualifier("analysisJobExecutor") Executor executor,
      Clock clock,
      PricingSnapshotIdentityService pricingIdentityService,
      AnalysisPersistenceService analysisPersistenceService) {
    this.jobRepository = jobRepository;
    this.executionService = executionService;
    this.executor = executor;
    this.clock = clock;
    this.pricingIdentityService = pricingIdentityService;
    this.analysisPersistenceService = analysisPersistenceService;
  }

  /**
   * Submits a new job for the given URL.
   *
   * @throws RepositoryIntakeException with {@link RepositoryIntakeErrorCode#INVALID_URL} if the URL
   *     fails parsing, or {@link RepositoryIntakeErrorCode#RATE_LIMITED} if the executor cannot
   *     accept the task.
   */
  public AnalysisJobSnapshot submit(String rawRepositoryUrl) {
    // Validate first — invalid URLs MUST NOT create a job row.
    GitHubRepositoryUrl repositoryUrl = GitHubRepositoryUrl.parse(rawRepositoryUrl);

    AnalysisJobId id = AnalysisJobId.random();
    Instant now = Instant.now(clock);
    AnalysisJobSnapshot initial =
        new AnalysisJobSnapshot(
            id,
            repositoryUrl.normalizedUrl(),
            AnalysisJobStatus.QUEUED,
            AnalysisJobPhase.QUEUED,
            0,
            "Job queued",
            null,
            null,
            null,
            AnalysisJobMetrics.empty(),
            now,
            null,
            now,
            null);
    AnalysisJobSnapshot persisted = jobRepository.save(initial);

    PricingSnapshotHandle pricingHandle = pricingIdentityService.capture();
    Optional<UUID> cachedAnalysisId =
        analysisPersistenceService.findLatestSuccessIdFor(
            repositoryUrl.normalizedUrl(), pricingHandle.id().value());
    if (cachedAnalysisId.isPresent()) {
      LOGGER.info(
          "reusing cached analysis {} for repo {} under snapshot {} — skipping pipeline",
          cachedAnalysisId.get(),
          repositoryUrl.normalizedUrl(),
          pricingHandle.id().value());
      jobRepository.updatePricing(id, pricingHandle);
      jobRepository.markSuccess(
          id, cachedAnalysisId.get(), AnalysisJobMetrics.empty(), Instant.now(clock));
      return jobRepository.findById(id).orElse(persisted);
    }

    try {
      executor.execute(() -> executionService.runJob(id));
    } catch (RejectedExecutionException ex) {
      // Spring's TaskRejectedException extends RejectedExecutionException, so this single catch
      // covers both. After the concurrent-analysis-limits change, slot contention NEVER raises this
      // exception (a free queue slot enqueues the task); rejection only fires when the executor's
      // internal LinkedBlockingQueue reached `tokenmeter.analyze-throttle.queue-capacity`. We roll
      // back the row to honour the spec invariant "no orphan job row on 429".
      LOGGER.warn("queue ceiling reached for jobId={} — rolling back row", id);
      jobRepository.deleteById(id.value());
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.RATE_LIMITED,
          "Analysis queue is full. Please retry shortly.",
          ex);
    }
    return persisted;
  }
}
