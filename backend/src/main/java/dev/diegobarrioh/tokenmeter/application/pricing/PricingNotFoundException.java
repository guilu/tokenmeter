package dev.diegobarrioh.tokenmeter.application.pricing;

import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;

public class PricingNotFoundException extends RuntimeException {
  public PricingNotFoundException(AiProvider provider, String model) {
    super("Pricing not found for provider %s and model %s".formatted(provider.configKey(), model));
  }
}
