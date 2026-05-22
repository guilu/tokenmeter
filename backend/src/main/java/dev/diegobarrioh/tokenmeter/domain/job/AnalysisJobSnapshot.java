package dev.diegobarrioh.tokenmeter.domain.job;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable view of an {@code AnalysisJob} at a point in time. Used as the boundary record
 * between the application and infrastructure layers; carries no behaviour beyond holding state.
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
    Instant completedAt) {

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
}
