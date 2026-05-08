package dev.diegobarrioh.tokenmeter.infrastructure.web.pricing;

import java.math.BigDecimal;

public record PricingModelResponse(
    String provider,
    String model,
    BigDecimal inputTokenPricePerMillion,
    BigDecimal outputTokenPricePerMillion) {}
