package dev.diegobarrioh.tokenmeter.application.pricing.refresh;

import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import java.util.List;

/**
 * Outbound port used by {@link PricingRefreshService} to persist a fresh batch of REMOTE snapshots.
 * The implementation is responsible for replacing every REMOTE (and FALLBACK) row atomically inside
 * a single transaction.
 */
public interface PricingSnapshotStorePort {

  /**
   * Replaces all REMOTE and FALLBACK rows with the supplied snapshots in a single transaction. If
   * any insert fails, every staged change MUST be rolled back so the table reflects its pre-refresh
   * state.
   */
  void replaceRemote(List<PricingSnapshot> snapshots);
}
