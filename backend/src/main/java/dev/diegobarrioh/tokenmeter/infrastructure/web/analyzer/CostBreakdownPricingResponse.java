package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.math.BigDecimal;

public record CostBreakdownPricingResponse(
    BigDecimal inputTokenPricePerMillion, BigDecimal outputTokenPricePerMillion) {}
