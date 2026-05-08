package dev.diegobarrioh.tokenmeter.infrastructure.web.pricing;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import java.util.Comparator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {
  private final PricingProvider pricingProvider;

  public PricingController(PricingProvider pricingProvider) {
    this.pricingProvider = pricingProvider;
  }

  @GetMapping
  public PricingResponse getPricing() {
    return new PricingResponse(
        pricingProvider.all().stream()
            .sorted(
                Comparator.comparing((ModelPricing pricing) -> pricing.provider().configKey())
                    .thenComparing(ModelPricing::model))
            .map(this::toResponse)
            .toList());
  }

  private PricingModelResponse toResponse(ModelPricing pricing) {
    return new PricingModelResponse(
        pricing.provider().configKey(),
        pricing.model(),
        pricing.inputTokenPricePerMillion(),
        pricing.outputTokenPricePerMillion());
  }
}
