package dev.diegobarrioh.tokenmeter.domain.job;

/**
 * Closed set of statuses for an {@code AnalysisJob}. {@code SUCCESS} and {@code FAILED} are
 * terminal.
 */
public enum AnalysisJobStatus {
  QUEUED,
  RUNNING,
  SUCCESS,
  FAILED;

  /** Whether the job has reached a terminal status (no further transitions allowed). */
  public boolean isTerminal() {
    return this == SUCCESS || this == FAILED;
  }
}
