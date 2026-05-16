package dev.diegobarrioh.tokenmeter.application.pricing.refresh;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Outcome of a single pricing refresh invocation. {@code failed} is reserved for partial failure
 * accounting: in the current implementation a refresh either commits every mapped row ({@code
 * failed == 0}) or rolls back entirely (the caller observes the thrown exception, not this record).
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
