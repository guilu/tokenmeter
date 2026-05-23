package dev.diegobarrioh.tokenmeter.domain.job;

/**
 * Volatile, on-read snapshot of the executor queue that contextualises a single {@link
 * AnalysisJobSnapshot}.
 *
 * <p>Values are computed when the polling endpoint resolves a job and are NOT persisted: they may
 * vary between successive polls because new submissions may arrive or jobs ahead may complete.
 *
 * <p>{@code queuePosition} is 1-based and MUST be present only when the snapshot status is {@code
 * QUEUED}; when the job is {@code RUNNING} the position is {@code null} because no queue context
 * applies. Terminal snapshots ({@code SUCCESS}/{@code FAILED}) carry no {@code
 * AnalysisJobQueueState} at all (the wrapping {@link AnalysisJobView} stores {@code null}).
 */
public record AnalysisJobQueueState(int runningCount, int maxConcurrency, Integer queuePosition) {

  public AnalysisJobQueueState {
    if (runningCount < 0) {
      throw new IllegalArgumentException("runningCount must be >= 0 (was " + runningCount + ")");
    }
    if (maxConcurrency < 1) {
      throw new IllegalArgumentException(
          "maxConcurrency must be >= 1 (was " + maxConcurrency + ")");
    }
    if (queuePosition != null && queuePosition < 1) {
      throw new IllegalArgumentException(
          "queuePosition must be null or >= 1 (was " + queuePosition + ")");
    }
  }
}
