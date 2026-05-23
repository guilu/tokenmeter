package dev.diegobarrioh.tokenmeter.domain.job;

/**
 * Optional pipeline metrics reported alongside the job snapshot. Each field is nullable until the
 * relevant phase has produced a value. Once non-null, a metric MUST never regress to {@code null}.
 * All non-null values MUST be non-negative.
 */
public record AnalysisJobMetrics(
    Long filesDiscovered,
    Long filesProcessed,
    Long filesSkipped,
    Long tokensCounted,
    Integer contextWindows,
    Integer pricingModelsProcessed) {

  public AnalysisJobMetrics {
    requireNonNegative("filesDiscovered", filesDiscovered);
    requireNonNegative("filesProcessed", filesProcessed);
    requireNonNegative("filesSkipped", filesSkipped);
    requireNonNegative("tokensCounted", tokensCounted);
    requireNonNegative("contextWindows", contextWindows);
    requireNonNegative("pricingModelsProcessed", pricingModelsProcessed);
  }

  /** All-null snapshot — useful as initial state. */
  public static AnalysisJobMetrics empty() {
    return new AnalysisJobMetrics(null, null, null, null, null, null);
  }

  private static void requireNonNegative(String field, Long value) {
    if (value != null && value < 0L) {
      throw new IllegalArgumentException(field + " must be >= 0 (was " + value + ")");
    }
  }

  private static void requireNonNegative(String field, Integer value) {
    if (value != null && value < 0) {
      throw new IllegalArgumentException(field + " must be >= 0 (was " + value + ")");
    }
  }
}
