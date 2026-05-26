package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.math.BigDecimal;

public record CostByModeEntry(String mode, BigDecimal totalCost, long analysisCount) {}
