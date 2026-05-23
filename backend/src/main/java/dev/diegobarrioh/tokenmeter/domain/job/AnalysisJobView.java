package dev.diegobarrioh.tokenmeter.domain.job;

import java.util.Objects;

/**
 * Composition of a persisted {@link AnalysisJobSnapshot} with the volatile {@link
 * AnalysisJobQueueState} computed at the moment of reading.
 *
 * <p>The split is intentional: {@code snapshot} reflects the row in {@code analysis_job} and is
 * deterministic given that row, while {@code queueState} is derived from {@code count(*)} queries
 * over the whole table and may differ across polls of the same job. Keeping them in separate
 * records preserves the two semantics — "no longer applies" vs "not computed" — without overloading
 * a single nullable field.
 *
 * <p>{@code queueState} is {@code null} when the snapshot status is terminal ({@code SUCCESS} or
 * {@code FAILED}); otherwise it is non-null.
 */
public record AnalysisJobView(AnalysisJobSnapshot snapshot, AnalysisJobQueueState queueState) {

  public AnalysisJobView {
    Objects.requireNonNull(snapshot, "snapshot must not be null");
  }
}
