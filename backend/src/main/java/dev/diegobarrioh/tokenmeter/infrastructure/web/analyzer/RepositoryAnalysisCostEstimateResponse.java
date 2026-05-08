package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.math.BigDecimal;

public record RepositoryAnalysisCostEstimateResponse(
    String provider,
    String model,
    String mode,
    long baseTokens,
    long estimatedInputTokens,
    long estimatedOutputTokens,
    BigDecimal inputCost,
    BigDecimal outputCost,
    BigDecimal totalCost,
    String formula) {}
