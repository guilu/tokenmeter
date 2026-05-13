package dev.diegobarrioh.tokenmeter.application.cost;

import java.math.BigDecimal;
import java.util.Objects;

public record EngineeringEffortEstimate(
    BigDecimal seniorEngineerHours,
    BigDecimal engineeringDays,
    String manualImplementationEffort,
    String summary,
    String formula,
    EngineeringEffortAssumptions assumptions) {
  public EngineeringEffortEstimate {
    Objects.requireNonNull(seniorEngineerHours, "senior engineer hours are required");
    Objects.requireNonNull(engineeringDays, "engineering days are required");
    if (manualImplementationEffort == null || manualImplementationEffort.isBlank()) {
      throw new IllegalArgumentException("manual implementation effort is required");
    }
    if (summary == null || summary.isBlank()) {
      throw new IllegalArgumentException("summary is required");
    }
    if (formula == null || formula.isBlank()) {
      throw new IllegalArgumentException("formula is required");
    }
    Objects.requireNonNull(assumptions, "assumptions are required");
    manualImplementationEffort = manualImplementationEffort.trim();
    summary = summary.trim();
    formula = formula.trim();
  }
}
