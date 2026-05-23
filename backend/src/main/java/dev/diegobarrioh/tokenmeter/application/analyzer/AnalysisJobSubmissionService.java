package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import dev.diegobarrioh.tokenmeter.domain.repository.GitHubRepositoryUrl;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.time.Clock;
import java.time.Instant;
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

  public AnalysisJobSubmissionService(
      AnalysisJobRepository jobRepository,
      AnalysisJobExecutionService executionService,
      @Qualifier("analysisJobExecutor") Executor executor,
      Clock clock) {
    this.jobRepository = jobRepository;
    this.executionService = executionService;
    this.executor = executor;
    this.clock = clock;
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

    try {
      executor.execute(() -> executionService.runJob(id));
    } catch (RejectedExecutionException ex) {
      // Spring's TaskRejectedException extends RejectedExecutionException, so this single catch
      // covers both. We roll back the row to honour the spec invariant "no orphan job row on 429".
      LOGGER.warn("Analysis executor rejected jobId={} — rolling back row", id);
      jobRepository.deleteById(id.value());
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.RATE_LIMITED,
          "Server is busy: analysis queue full. Please retry shortly.",
          ex);
    }
    return persisted;
  }
}
