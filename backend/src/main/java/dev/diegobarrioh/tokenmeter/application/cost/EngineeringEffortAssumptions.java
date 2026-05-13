package dev.diegobarrioh.tokenmeter.application.cost;

import java.math.BigDecimal;
import java.util.Objects;

public record EngineeringEffortAssumptions(
    BigDecimal tokensPerSeniorEngineerHour,
    BigDecimal hoursPerEngineeringDay,
    BigDecimal modeComplexityMultiplier) {
  public EngineeringEffortAssumptions {
    Objects.requireNonNull(
        tokensPerSeniorEngineerHour, "tokens per senior engineer hour is required");
    Objects.requireNonNull(hoursPerEngineeringDay, "hours per engineering day is required");
    Objects.requireNonNull(modeComplexityMultiplier, "mode complexity multiplier is required");
  }
}
