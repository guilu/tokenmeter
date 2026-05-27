package dev.diegobarrioh.tokenmeter.domain.job;

import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;

/**
 * Stable error identifiers persisted on a failed {@code AnalysisJob}. The frontend's {@code
 * toUserMessage} mapper consumes these codes.
 */
public enum AnalysisJobErrorCode {
  CLONE_TIMEOUT,
  REPOSITORY_TOO_LARGE,
  INVALID_URL,
  ANALYSIS_FAILED,
  JOB_INTERRUPTED,
  RATE_LIMITED;

  /**
   * Maps an intake-layer error code into a job-level error code. Unknown intake codes (including
   * {@code REPOSITORY_NOT_ACCESSIBLE} and {@code CLONE_FAILED}) are folded into the generic {@link
   * #ANALYSIS_FAILED} bucket — callers should set a descriptive {@code errorMessage}.
   */
  public static AnalysisJobErrorCode fromIntakeCode(RepositoryIntakeErrorCode intakeCode) {
    if (intakeCode == null) {
      return ANALYSIS_FAILED;
    }
    return switch (intakeCode) {
      case INVALID_URL -> INVALID_URL;
      case REPOSITORY_TOO_LARGE -> REPOSITORY_TOO_LARGE;
      case CLONE_TIMEOUT -> CLONE_TIMEOUT;
      case RATE_LIMITED -> RATE_LIMITED;
      case REPOSITORY_NOT_ACCESSIBLE, CLONE_FAILED, GITHUB_RATE_LIMITED, GITHUB_UNAVAILABLE ->
          ANALYSIS_FAILED;
    };
  }
}
