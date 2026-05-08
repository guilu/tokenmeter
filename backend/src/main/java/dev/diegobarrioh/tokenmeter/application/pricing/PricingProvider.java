package dev.diegobarrioh.tokenmeter.application.pricing;

import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import java.util.List;
import java.util.Optional;

public interface PricingProvider {
  List<ModelPricing> all();

  Optional<ModelPricing> find(AiProvider provider, String model);

  default ModelPricing require(AiProvider provider, String model) {
    return find(provider, model).orElseThrow(() -> new PricingNotFoundException(provider, model));
  }
}
