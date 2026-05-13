package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.math.BigDecimal;

public record EngineeringEffortEstimateResponse(
    BigDecimal seniorEngineerHours,
    BigDecimal engineeringDays,
    String manualImplementationEffort,
    String summary,
    String formula,
    EngineeringEffortAssumptionsResponse assumptions) {}
