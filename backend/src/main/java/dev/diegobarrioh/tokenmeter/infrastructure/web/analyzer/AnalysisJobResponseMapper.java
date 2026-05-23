package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Maps domain {@link AnalysisJobSnapshot} into its wire representation. */
@Component
public class AnalysisJobResponseMapper {

  private static final Map<AnalysisJobPhase, String> PHASE_LABELS = phaseLabels();

  /** Builds the 202 body for {@code POST /api/analyze}. */
  public AnalysisJobAcceptedResponse toAccepted(AnalysisJobSnapshot snapshot) {
    String jobId = snapshot.id().value().toString();
    String statusUrl = "/api/analyze/jobs/" + jobId;
    return new AnalysisJobAcceptedResponse(jobId, snapshot.status(), statusUrl, null);
  }

  /** Builds the 200 body for {@code GET /api/analyze/jobs/{jobId}}. */
  public AnalysisJobStatusResponse toStatus(AnalysisJobSnapshot snapshot) {
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

    return new AnalysisJobStatusResponse(
        jobId,
        snapshot.status(),
        snapshot.phase(),
        PHASE_LABELS.getOrDefault(snapshot.phase(), snapshot.phase().name()),
        snapshot.progressPercent(),
        snapshot.message(),
        snapshot.analysisId(),
        error,
        metricsResponse,
        timestamps);
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
