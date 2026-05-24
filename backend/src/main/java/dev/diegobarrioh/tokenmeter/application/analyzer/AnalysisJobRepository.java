package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobErrorCode;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application port for persisting and querying {@code AnalysisJob} state. The infrastructure layer
 * provides the JPA adapter.
 */
public interface AnalysisJobRepository {

  /** Persists an initial {@code QUEUED} snapshot. */
  AnalysisJobSnapshot save(AnalysisJobSnapshot initialQueued);

  /** Marks the job as having started running (status=RUNNING, startedAt=when). */
  void markStarted(AnalysisJobId id, Instant when);

  /**
   * Updates the phase/progress/message of a non-terminal job. {@code progressPercent} is expected
   * to be already clamped to {@code [0, 99]} by the caller.
   */
  void updatePhase(AnalysisJobId id, AnalysisJobPhase phase, int progressPercent, String message);

  /** Merges the given metrics into the job; never regresses a non-null field to {@code null}. */
  void updateMetrics(AnalysisJobId id, AnalysisJobMetrics metrics);

  /**
   * Persists the captured {@link PricingSnapshotHandle} on the job row. No-op on terminal jobs.
   * Used by the worker at the start of {@code CALCULATING_COSTS} so the job and its eventual
   * analysis row share the same pricing snapshot identifier.
   */
  void updatePricing(AnalysisJobId id, PricingSnapshotHandle handle);

  /** Marks the job as successful with the produced {@code analysisId} (progress=100). */
  void markSuccess(
      AnalysisJobId id, UUID analysisId, AnalysisJobMetrics finalMetrics, Instant when);

  /** Marks the job as failed with the given code/message. No-op if the job is already terminal. */
  void markFailed(AnalysisJobId id, AnalysisJobErrorCode code, String message, Instant when);

  /**
   * Marks the job as failed with {@link AnalysisJobErrorCode#JOB_INTERRUPTED}, used by the reaper
   * on startup for jobs left non-terminal by a previous process.
   */
  void markInterrupted(AnalysisJobId id, Instant when);

  Optional<AnalysisJobSnapshot> findById(AnalysisJobId id);

  /** All jobs whose status is still {@code QUEUED} or {@code RUNNING}. */
  List<AnalysisJobSnapshot> findNonTerminal();

  /**
   * Deletes terminal jobs of the given {@code status} whose {@code completedAt} is older than the
   * cutoff. Returns the number of rows removed.
   */
  int deleteCompletedBefore(
      dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus status, Instant cutoff);

  /**
   * Removes a single job by raw id. Used to roll back a row when executor submission is rejected.
   */
  void deleteById(UUID id);

  /**
   * Returns the number of rows in {@code analysis_job} whose {@code status} equals the given
   * argument. Used by the polling endpoint to compute {@code queueState.runningCount}.
   */
  int countByStatus(AnalysisJobStatus status);

  /**
   * Returns the 1-based FIFO position of a {@code QUEUED} job among all queued rows, ordered by
   * {@code (createdAt ASC, id ASC)}. Returns {@code 0} when the referenced job does not exist or is
   * no longer in {@code QUEUED} status (e.g. it was promoted to {@code RUNNING} between the
   * snapshot read and this call). Callers MUST treat the result as a best-effort estimate that may
   * vary between successive polls.
   */
  int countQueuedAheadOf(AnalysisJobId targetId);
}
