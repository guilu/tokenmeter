package dev.diegobarrioh.tokenmeter.domain.job;

/**
 * Closed set of phases through which an {@code AnalysisJob} progresses.
 *
 * <p>Happy-path order is strictly forward and matches the declaration order from {@link #QUEUED}
 * through {@link #COMPLETED}. Forward jumps (skipping intermediate phases) are legal — a phase that
 * is a no-op may be skipped — but the relative order MUST never regress. From any non-terminal
 * phase the job MAY transition directly to {@link #FAILED}. {@link #COMPLETED} and {@link #FAILED}
 * are terminal.
 */
public enum AnalysisJobPhase {
  QUEUED,
  CHECKING_CACHE,
  CLONING_REPOSITORY,
  SCANNING_FILES,
  FILTERING_FILES,
  COUNTING_TOKENS,
  CALCULATING_COSTS,
  SAVING_REPORT,
  COMPLETED,
  FAILED;

  /** Whether the phase is a terminal one (no further transitions allowed). */
  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED;
  }

  /**
   * Rank used to compare two phases on the happy path. {@link #FAILED} is intentionally outside
   * this rank because it can be reached from any non-terminal phase and does not participate in the
   * forward-only ordering.
   */
  public int forwardRank() {
    if (this == FAILED) {
      // FAILED is sortable side-channel; callers compare ranks only against non-terminal phases.
      return Integer.MAX_VALUE;
    }
    return ordinal();
  }

  /**
   * Validates a candidate transition from {@code this} phase to {@code next}.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>If {@code this} is terminal, no transitions are allowed.
   *   <li>{@link #FAILED} is reachable from any non-terminal phase.
   *   <li>Otherwise, {@code next} MUST advance forward along the canonical order.
   * </ul>
   */
  public boolean canTransitionTo(AnalysisJobPhase next) {
    if (next == null) {
      return false;
    }
    if (this == next) {
      return false;
    }
    if (this.isTerminal()) {
      return false;
    }
    if (next == FAILED) {
      return true;
    }
    if (next == QUEUED) {
      return false;
    }
    return next.ordinal() > this.ordinal();
  }
}
