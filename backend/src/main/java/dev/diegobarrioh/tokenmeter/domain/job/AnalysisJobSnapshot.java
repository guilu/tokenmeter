package dev.diegobarrioh.tokenmeter.domain.job;

import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotId;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable view of an {@code AnalysisJob} at a point in time. Used as the boundary record between
 * the application and infrastructure layers; carries no behaviour beyond holding state.
 *
 * <p>The optional {@code pricing} block is populated only once the worker enters {@code
 * CALCULATING_COSTS} and persists the captured {@link PricingSnapshotId}. Pre-cost phases (and
 * legacy rows inserted before TKM-48) leave it {@code null}.
 */
public record AnalysisJobSnapshot(
    AnalysisJobId id,
    String repositoryUrl,
    AnalysisJobStatus status,
    AnalysisJobPhase phase,
    int progressPercent,
    String message,
    UUID analysisId,
    AnalysisJobErrorCode errorCode,
    String errorMessage,
    AnalysisJobMetrics metrics,
    Instant createdAt,
    Instant startedAt,
    Instant updatedAt,
    Instant completedAt,
    PricingSnapshotCapture pricing) {

  public AnalysisJobSnapshot {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }
    if (repositoryUrl == null || repositoryUrl.isBlank()) {
      throw new IllegalArgumentException("repositoryUrl must not be blank");
    }
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    if (phase == null) {
      throw new IllegalArgumentException("phase must not be null");
    }
    if (progressPercent < 0 || progressPercent > 100) {
      throw new IllegalArgumentException(
          "progressPercent must be within [0, 100] (was " + progressPercent + ")");
    }
    if (metrics == null) {
      metrics = AnalysisJobMetrics.empty();
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("updatedAt must not be null");
    }
  }

  /** Back-compat constructor for callers that do not carry pricing metadata. */
  public AnalysisJobSnapshot(
      AnalysisJobId id,
      String repositoryUrl,
      AnalysisJobStatus status,
      AnalysisJobPhase phase,
      int progressPercent,
      String message,
      UUID analysisId,
      AnalysisJobErrorCode errorCode,
      String errorMessage,
      AnalysisJobMetrics metrics,
      Instant createdAt,
      Instant startedAt,
      Instant updatedAt,
      Instant completedAt) {
    this(
        id,
        repositoryUrl,
        status,
        phase,
        progressPercent,
        message,
        analysisId,
        errorCode,
        errorMessage,
        metrics,
        createdAt,
        startedAt,
        updatedAt,
        completedAt,
        null);
  }

  /**
   * Pricing snapshot identity captured at the start of {@code CALCULATING_COSTS}. {@code null}
   * until then.
   */
  public record PricingSnapshotCapture(
      PricingSnapshotId snapshotId, PricingSource primarySource, Instant capturedAt) {
    public PricingSnapshotCapture {
      if (snapshotId == null) {
        throw new IllegalArgumentException("snapshotId must not be null");
      }
      if (primarySource == null) {
        throw new IllegalArgumentException("primarySource must not be null");
      }
      if (capturedAt == null) {
        throw new IllegalArgumentException("capturedAt must not be null");
      }
    }
  }
}
