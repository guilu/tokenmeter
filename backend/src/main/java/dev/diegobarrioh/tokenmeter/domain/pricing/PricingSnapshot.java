package dev.diegobarrioh.tokenmeter.domain.pricing;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Wraps a {@link ModelPricing} value together with freshness metadata. {@code externalModelId} may
 * be {@code null} for {@link PricingSource#FALLBACK} and {@link PricingSource#OVERRIDE} entries.
 */
public record PricingSnapshot(
    ModelPricing pricing, PricingSource source, OffsetDateTime fetchedAt, String externalModelId) {

  public PricingSnapshot {
    Objects.requireNonNull(pricing, "pricing is required");
    Objects.requireNonNull(source, "source is required");
    Objects.requireNonNull(fetchedAt, "fetchedAt is required");
  }

  public AiProvider provider() {
    return pricing.provider();
  }

  public String model() {
    return pricing.model();
  }
}
