package dev.diegobarrioh.tokenmeter.application.pricing.refresh;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Published on the application event bus after a successful pricing refresh. Consumers (UI cache
 * invalidation, audit logging, etc.) can listen to it without coupling to the refresh service.
 *
 * @param fetchedAt the instant the refresh ran against the upstream source
 * @param updated number of REMOTE rows written to {@code model_pricing}
 * @param skipped number of configured mappings that could not be updated (missing upstream key,
 *     null cost, etc.)
 */
public record PricingRefreshedEvent(OffsetDateTime fetchedAt, int updated, int skipped) {

  public PricingRefreshedEvent {
    Objects.requireNonNull(fetchedAt, "fetchedAt is required");
    if (updated < 0) {
      throw new IllegalArgumentException("updated must be >= 0");
    }
    if (skipped < 0) {
      throw new IllegalArgumentException("skipped must be >= 0");
    }
  }
}
