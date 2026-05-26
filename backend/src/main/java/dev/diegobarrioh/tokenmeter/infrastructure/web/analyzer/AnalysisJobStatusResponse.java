package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire representation of an {@code AnalysisJob} snapshot served by {@code GET
 * /api/analyze/jobs/{jobId}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisJobStatusResponse(
    String jobId,
    AnalysisJobStatus status,
    AnalysisJobPhase phase,
    String phaseLabel,
    int progressPercent,
    String message,
    UUID analysisId,
    JobErrorResponse error,
    JobMetricsResponse metrics,
    JobTimestampsResponse timestamps,
    QueueStateResponse queueState,
    PricingMetadata pricing) {

  /** Populated only when {@code status = FAILED}. */
  public record JobErrorResponse(String code, String message) {}

  /** Optional pipeline metrics. Each field is nullable until the relevant phase fills it in. */
  public record JobMetricsResponse(
      Long filesDiscovered,
      Long filesProcessed,
      Long filesSkipped,
      Long tokensCounted,
      Integer contextWindows,
      Integer pricingModelsProcessed) {}

  /** Lifecycle timestamps. */
  public record JobTimestampsResponse(
      Instant createdAt, Instant startedAt, Instant updatedAt, Instant completedAt) {}

  /**
   * On-read view of the executor queue. Populated for QUEUED and RUNNING jobs; absent (null) for
   * terminal jobs. {@code queuePosition} is present only when {@code status = QUEUED}.
   */
  public record QueueStateResponse(int runningCount, int maxConcurrency, Integer queuePosition) {}
}
