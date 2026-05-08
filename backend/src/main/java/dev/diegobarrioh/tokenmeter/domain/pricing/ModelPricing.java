package dev.diegobarrioh.tokenmeter.domain.pricing;

import java.math.BigDecimal;
import java.util.Objects;

public record ModelPricing(
    AiProvider provider,
    String model,
    BigDecimal inputTokenPricePerMillion,
    BigDecimal outputTokenPricePerMillion) {
  public ModelPricing {
    Objects.requireNonNull(provider, "provider is required");
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model is required");
    }
    Objects.requireNonNull(inputTokenPricePerMillion, "input token price is required");
    Objects.requireNonNull(outputTokenPricePerMillion, "output token price is required");
    if (inputTokenPricePerMillion.signum() < 0) {
      throw new IllegalArgumentException("input token price must be positive or zero");
    }
    if (outputTokenPricePerMillion.signum() < 0) {
      throw new IllegalArgumentException("output token price must be positive or zero");
    }
    model = model.trim();
  }
}
