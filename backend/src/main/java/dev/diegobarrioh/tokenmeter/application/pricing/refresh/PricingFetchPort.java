package dev.diegobarrioh.tokenmeter.application.pricing.refresh;

import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import java.time.OffsetDateTime;

/**
 * Outbound port used by {@link PricingRefreshService} to pull a fresh batch of {@link
 * PricingSnapshot} values from an external pricing source (LiteLLM in production). Keeps the
 * application layer free of any HTTP / mapping infrastructure types.
 *
 * <p>Implementations MUST tag every returned snapshot with {@link
 * dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource#REMOTE} and propagate the supplied
 * {@code fetchedAt} into every snapshot they emit, so callers can rely on a consistent timestamp
 * across the whole batch.
 */
public interface PricingFetchPort {

  /**
   * Fetches the upstream catalogue and converts it into snapshots ready to be persisted, alongside
   * the number of upstream entries that were skipped. The implementation is expected to throw
   * {@link PricingRefreshException} (or a runtime subclass) when the fetch / parsing / mapping
   * cannot complete.
   *
   * @param fetchedAt the timestamp every produced snapshot MUST carry
   * @return mapped snapshots and skip count; never {@code null}
   */
  PricingFetchResult fetchAndMap(OffsetDateTime fetchedAt);
}
