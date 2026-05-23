package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;

/**
 * Thrown when {@code GET /api/analyze/jobs/{jobId}} is asked for a non-existent or expired job. The
 * web layer translates this into HTTP {@code 404}.
 */
public class AnalysisJobNotFoundException extends RuntimeException {

  private final AnalysisJobId jobId;

  public AnalysisJobNotFoundException(AnalysisJobId jobId) {
    super("Analysis job not found: " + jobId);
    this.jobId = jobId;
  }

  public AnalysisJobId jobId() {
    return jobId;
  }
}
