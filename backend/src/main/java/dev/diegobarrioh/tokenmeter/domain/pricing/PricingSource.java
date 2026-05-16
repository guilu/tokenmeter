package dev.diegobarrioh.tokenmeter.domain.pricing;

/**
 * Origin of a {@link ModelPricing} entry. Values are declared in strict precedence order: an {@code
 * OVERRIDE} row shadows a {@code REMOTE} row, which shadows a {@code FALLBACK} row.
 */
public enum PricingSource {
  OVERRIDE,
  REMOTE,
  FALLBACK
}
