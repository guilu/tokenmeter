package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobQueueState;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobView;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Read-only access to an {@code AnalysisJob} snapshot, used by the polling endpoint. */
@Service
public class AnalysisJobQueryService {

  private final AnalysisJobRepository jobRepository;
  private final AnalyzeThrottleProperties throttleProperties;

  public AnalysisJobQueryService(
      AnalysisJobRepository jobRepository, AnalyzeThrottleProperties throttleProperties) {
    this.jobRepository = jobRepository;
    this.throttleProperties = throttleProperties;
  }

  public Optional<AnalysisJobSnapshot> findById(AnalysisJobId id) {
    return jobRepository.findById(id);
  }

  /**
   * Resolves the snapshot plus the on-read queue context exposed by the polling endpoint.
   *
   * <p>Terminal jobs (SUCCESS, FAILED) carry {@code queueState = null}. RUNNING jobs expose {@code
   * runningCount} and {@code maxConcurrency} but no {@code queuePosition}. QUEUED jobs additionally
   * include the FIFO {@code queuePosition}.
   */
  public Optional<AnalysisJobView> getView(AnalysisJobId id) {
    return jobRepository
        .findById(id)
        .map(
            snapshot -> {
              AnalysisJobStatus status = snapshot.status();
              if (status.isTerminal()) {
                return new AnalysisJobView(snapshot, null);
              }
              int runningCount = jobRepository.countByStatus(AnalysisJobStatus.RUNNING);
              int maxConcurrency = throttleProperties.maxConcurrent();
              Integer queuePosition =
                  status == AnalysisJobStatus.QUEUED ? jobRepository.countQueuedAheadOf(id) : null;
              if (queuePosition != null && queuePosition < 1) {
                // Race: the job was promoted to RUNNING between the snapshot read and the
                // position lookup. Fall back to "no position" rather than violating the
                // AnalysisJobQueueState invariant.
                queuePosition = null;
              }
              return new AnalysisJobView(
                  snapshot, new AnalysisJobQueueState(runningCount, maxConcurrency, queuePosition));
            });
  }
}
