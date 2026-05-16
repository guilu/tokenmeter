package dev.diegobarrioh.tokenmeter.application.pricing;

import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public interface PricingProvider {
  List<ModelPricing> all();

  Optional<ModelPricing> find(AiProvider provider, String model);

  /**
   * Returns pricing entries enriched with freshness metadata. The default implementation wraps
   * every entry from {@link #all()} with {@link PricingSource#FALLBACK} and the current instant so
   * that legacy implementations remain valid without modification.
   */
  default List<PricingSnapshot> snapshots() {
    OffsetDateTime fetchedAt = OffsetDateTime.now(ZoneOffset.UTC);
    return all().stream()
        .map(pricing -> new PricingSnapshot(pricing, PricingSource.FALLBACK, fetchedAt, null))
        .toList();
  }

  default ModelPricing require(AiProvider provider, String model) {
    return find(provider, model).orElseThrow(() -> new PricingNotFoundException(provider, model));
  }
}
