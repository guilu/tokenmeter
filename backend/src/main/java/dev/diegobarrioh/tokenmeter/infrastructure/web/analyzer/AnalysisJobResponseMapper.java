package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobQueueState;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobView;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Maps domain {@link AnalysisJobSnapshot} / {@link AnalysisJobView} into wire representation. */
@Component
public class AnalysisJobResponseMapper {

  private static final Map<AnalysisJobPhase, String> PHASE_LABELS = phaseLabels();
  private static final String WAITING_FOR_SLOT_LABEL = "Waiting for an analysis slot";

  /** Builds the 202 body for {@code POST /api/analyze}. */
  public AnalysisJobAcceptedResponse toAccepted(AnalysisJobSnapshot snapshot) {
    String jobId = snapshot.id().value().toString();
    String statusUrl = "/api/analyze/jobs/" + jobId;
    return new AnalysisJobAcceptedResponse(
        jobId, snapshot.status(), statusUrl, snapshot.analysisId());
  }

  /** Builds the 200 body for {@code GET /api/analyze/jobs/{jobId}}. */
  public AnalysisJobStatusResponse toStatus(AnalysisJobView view) {
    AnalysisJobSnapshot snapshot = view.snapshot();
    String jobId = snapshot.id().value().toString();
    AnalysisJobStatusResponse.JobErrorResponse error =
        snapshot.status() == AnalysisJobStatus.FAILED
            ? new AnalysisJobStatusResponse.JobErrorResponse(
                snapshot.errorCode() == null ? "ANALYSIS_FAILED" : snapshot.errorCode().name(),
                snapshot.errorMessage() == null ? "Analysis failed" : snapshot.errorMessage())
            : null;

    AnalysisJobMetrics metrics =
        snapshot.metrics() == null ? AnalysisJobMetrics.empty() : snapshot.metrics();
    AnalysisJobStatusResponse.JobMetricsResponse metricsResponse =
        new AnalysisJobStatusResponse.JobMetricsResponse(
            metrics.filesDiscovered(),
            metrics.filesProcessed(),
            metrics.filesSkipped(),
            metrics.tokensCounted(),
            metrics.contextWindows(),
            metrics.pricingModelsProcessed());

    AnalysisJobStatusResponse.JobTimestampsResponse timestamps =
        new AnalysisJobStatusResponse.JobTimestampsResponse(
            snapshot.createdAt(),
            snapshot.startedAt(),
            snapshot.updatedAt(),
            snapshot.completedAt());

    AnalysisJobQueueState queueState = view.queueState();
    AnalysisJobStatusResponse.QueueStateResponse queueStateResponse =
        queueState == null
            ? null
            : new AnalysisJobStatusResponse.QueueStateResponse(
                queueState.runningCount(), queueState.maxConcurrency(), queueState.queuePosition());

    String phaseLabel = resolvePhaseLabel(snapshot, queueState);

    return new AnalysisJobStatusResponse(
        jobId,
        snapshot.status(),
        snapshot.phase(),
        phaseLabel,
        snapshot.progressPercent(),
        snapshot.message(),
        snapshot.analysisId(),
        error,
        metricsResponse,
        timestamps,
        queueStateResponse,
        RepositoryAnalysisMapper.toPricingMetadata(snapshot.pricing()));
  }

  /**
   * Resolves the human-readable phase label. When the job is QUEUED AND we know the runningCount
   * has caught up with maxConcurrency the label switches to "Waiting for an analysis slot" — this
   * is the contention signal the UI shows under the spinner. In every other case (RUNNING, brief
   * QUEUED window before the executor pulls the task while a slot is still free, terminal) we fall
   * back to the per-phase label.
   *
   * <p>Note on the race window: between {@code save()} and {@code markStarted()} the snapshot may
   * report {@code QUEUED} while {@code runningCount < maxConcurrency} because no worker has yet
   * picked the task up. We intentionally keep the "Queued" label in that scenario; the next 1.5 s
   * poll will see the row as RUNNING.
   */
  private static String resolvePhaseLabel(
      AnalysisJobSnapshot snapshot, AnalysisJobQueueState queueState) {
    if (snapshot.status() == AnalysisJobStatus.QUEUED
        && queueState != null
        && queueState.runningCount() >= queueState.maxConcurrency()) {
      return WAITING_FOR_SLOT_LABEL;
    }
    return PHASE_LABELS.getOrDefault(snapshot.phase(), snapshot.phase().name());
  }

  private static Map<AnalysisJobPhase, String> phaseLabels() {
    EnumMap<AnalysisJobPhase, String> map = new EnumMap<>(AnalysisJobPhase.class);
    map.put(AnalysisJobPhase.QUEUED, "Queued");
    map.put(AnalysisJobPhase.CHECKING_CACHE, "Checking cache");
    map.put(AnalysisJobPhase.CLONING_REPOSITORY, "Cloning repository");
    map.put(AnalysisJobPhase.SCANNING_FILES, "Detecting languages");
    map.put(AnalysisJobPhase.FILTERING_FILES, "Parsing files");
    map.put(AnalysisJobPhase.COUNTING_TOKENS, "Counting tokens");
    map.put(AnalysisJobPhase.CALCULATING_COSTS, "Calculating pricing models");
    map.put(AnalysisJobPhase.SAVING_REPORT, "Generating estimates");
    map.put(AnalysisJobPhase.COMPLETED, "Completed");
    map.put(AnalysisJobPhase.FAILED, "Failed");
    return map;
  }
}
