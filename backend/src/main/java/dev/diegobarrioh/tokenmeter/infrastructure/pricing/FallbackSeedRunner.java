package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.pricing.JpaPricingSnapshotStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Idempotent application runner that seeds the {@code model_pricing} table from the YAML fallback
 * source the first time the application boots against an empty schema. Subsequent boots see {@code
 * store.count() > 0} and short-circuit.
 */
@Component
public class FallbackSeedRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(FallbackSeedRunner.class);

  private final JpaPricingSnapshotStore store;
  private final PricingProvider fallbackSeedSource;

  public FallbackSeedRunner(
      JpaPricingSnapshotStore store,
      @Qualifier("fallbackSeedSource") PricingProvider fallbackSeedSource) {
    this.store = store;
    this.fallbackSeedSource = fallbackSeedSource;
  }

  @Override
  public void run(ApplicationArguments args) {
    long existing = store.count();
    if (existing > 0) {
      LOG.debug(
          "model_pricing already has {} row(s); skipping fallback seed", Long.valueOf(existing));
      return;
    }
    List<PricingSnapshot> snapshots = fallbackSeedSource.snapshots();
    if (snapshots.isEmpty()) {
      LOG.warn("fallback seed source returned no snapshots; model_pricing remains empty");
      return;
    }
    store.replaceAll(snapshots);
    LOG.info("Seeded model_pricing with {} FALLBACK row(s) from YAML", snapshots.size());
  }
}
