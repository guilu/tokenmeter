package dev.diegobarrioh.tokenmeter.application.pricing.refresh;

import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of a {@link PricingFetchPort#fetchAndMap} call: the snapshots ready to persist plus the
 * number of upstream entries that were skipped (malformed price or unsupported provider). Lets the
 * refresh service report accurate {@code updated}/{@code skipped} counts without leaking any
 * mapping/infrastructure detail into the application layer.
 */
public record PricingFetchResult(List<PricingSnapshot> snapshots, int skipped) {

  public PricingFetchResult {
    Objects.requireNonNull(snapshots, "snapshots is required");
    if (skipped < 0) {
      throw new IllegalArgumentException("skipped must be >= 0");
    }
    snapshots = List.copyOf(snapshots);
  }
}
