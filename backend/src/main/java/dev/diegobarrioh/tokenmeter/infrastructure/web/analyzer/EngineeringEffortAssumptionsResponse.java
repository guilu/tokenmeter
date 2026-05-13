package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.math.BigDecimal;

public record EngineeringEffortAssumptionsResponse(
    BigDecimal tokensPerSeniorEngineerHour,
    BigDecimal hoursPerEngineeringDay,
    BigDecimal modeComplexityMultiplier) {}
