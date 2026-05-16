package dev.diegobarrioh.tokenmeter.infrastructure.web.pricing;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {

  private static final Comparator<PricingSnapshot> BY_PROVIDER_THEN_MODEL =
      Comparator.comparing((PricingSnapshot snapshot) -> snapshot.provider().configKey())
          .thenComparing(PricingSnapshot::model);

  private final PricingProvider pricingProvider;

  public PricingController(PricingProvider pricingProvider) {
    this.pricingProvider = pricingProvider;
  }

  @GetMapping
  public PricingResponse getPricing() {
    List<PricingSnapshot> snapshots =
        pricingProvider.snapshots().stream().sorted(BY_PROVIDER_THEN_MODEL).toList();
    return PricingResponse.from(snapshots);
  }
}
