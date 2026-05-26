package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobErrorCode;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import java.util.UUID;

/**
 * Application port that emits state transitions and metric updates against the persistent {@code
 * AnalysisJob} store. The infrastructure adapter runs every method in its own transaction ({@code
 * REQUIRES_NEW}) so HTTP pollers see fresh state.
 *
 * <p>Invariants enforced by the implementation:
 *
 * <ul>
 *   <li>{@link #transition} clamps {@code progressPercent} to {@code [0, 99]}; it MUST NEVER
 *       persist {@code 100} as part of an in-flight transition.
 *   <li>{@link #success} is the only method that persists {@code progressPercent = 100}, {@code
 *       status = SUCCESS} and {@code phase = COMPLETED}, alongside a non-null {@code analysisId}.
 *   <li>{@link #fail} is idempotent against jobs already in a terminal status.
 * </ul>
 */
public interface AnalysisJobProgressEmitter {

  void transition(AnalysisJobId id, AnalysisJobPhase phase, int progressPercent, String message);

  void updateMetrics(AnalysisJobId id, AnalysisJobMetrics metrics);

  void success(AnalysisJobId id, UUID analysisId, AnalysisJobMetrics finalMetrics);

  void fail(AnalysisJobId id, AnalysisJobErrorCode code, String message);

  /**
   * Persists the captured {@link PricingSnapshotHandle} on the {@code analysis_job} row in its own
   * {@code REQUIRES_NEW} transaction so HTTP pollers see the {@code pricing} block as soon as the
   * worker enters {@code CALCULATING_COSTS}. No-op on terminal jobs.
   */
  void markPricing(AnalysisJobId id, PricingSnapshotHandle handle);
}
