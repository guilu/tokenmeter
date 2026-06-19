package dev.diegobarrioh.tokenmeter.application.pricing.refresh;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Outcome of a single pricing refresh invocation, also serialized as the {@code POST
 * /api/admin/pricing/refresh} response body.
 *
 * <p>{@code updated} is the number of REMOTE rows written. {@code skipped} is the number of
 * upstream LiteLLM entries that could not be mapped — unsupported provider (outside the curated
 * allowlist) or null/non-positive price. Since TKM-65 the refresh iterates the full upstream
 * catalogue, so against the live payload {@code skipped} is expectedly large (hundreds–thousands),
 * dominated by unsupported providers; it is NOT a failure signal. {@code failed} is reserved for
 * partial failure accounting: today a refresh either commits every mapped row ({@code failed == 0})
 * or rolls back entirely (the caller observes the thrown exception, not this record).
 */
public record PricingRefreshResult(OffsetDateTime fetchedAt, int updated, int skipped, int failed) {

  public PricingRefreshResult {
    Objects.requireNonNull(fetchedAt, "fetchedAt is required");
    if (updated < 0) {
      throw new IllegalArgumentException("updated must be >= 0");
    }
    if (skipped < 0) {
      throw new IllegalArgumentException("skipped must be >= 0");
    }
    if (failed < 0) {
      throw new IllegalArgumentException("failed must be >= 0");
    }
  }
}
