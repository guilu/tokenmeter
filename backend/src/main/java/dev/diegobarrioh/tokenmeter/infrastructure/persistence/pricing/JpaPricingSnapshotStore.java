package dev.diegobarrioh.tokenmeter.infrastructure.persistence.pricing;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingConfigurationException;
import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingSnapshotStorePort;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence facade for the {@code model_pricing} table. Exposes coarse-grained operations used by
 * the seed runner ({@link #replaceAll(List)}) and by the future refresh service ({@link
 * #replaceRemote(List)}). OVERRIDE rows are intentionally never persisted: they are applied
 * in-memory by {@code CompositePricingProvider} at read time.
 */
@Service
public class JpaPricingSnapshotStore implements PricingSnapshotStorePort {

  private static final int PRICE_SCALE = 6;

  private final ModelPricingJpaRepository repository;

  public JpaPricingSnapshotStore(ModelPricingJpaRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public long count() {
    return repository.count();
  }

  @Transactional(readOnly = true)
  public List<PricingSnapshot> findAll() {
    return repository.findAllByOrderByProviderAscModelAsc().stream()
        .map(JpaPricingSnapshotStore::toSnapshot)
        .toList();
  }

  /**
   * Replaces every row in the table with the supplied snapshots. Reserved for the cold-start seed
   * path so the FALLBACK baseline can be materialised atomically without juggling REMOTE rows.
   */
  @Transactional
  public void replaceAll(List<PricingSnapshot> snapshots) {
    Objects.requireNonNull(snapshots, "snapshots");
    repository.deleteAllInBatch();
    repository.flush();
    persist(snapshots);
  }

  /**
   * Replaces the REMOTE and FALLBACK rows with the supplied snapshots, leaving any non-REMOTE /
   * non-FALLBACK row untouched. Implemented for the refresh pipeline that lands in Phase 3.
   *
   * <p>OVERRIDE rows are never persisted to {@code model_pricing} in this design, so the deletion
   * filter is effectively "everything currently in the table"; the explicit filter documents the
   * contract and survives future schema changes that could allow OVERRIDE rows to be stored.
   */
  @Override
  @Transactional
  public void replaceRemote(List<PricingSnapshot> snapshots) {
    Objects.requireNonNull(snapshots, "snapshots");
    List<ModelPricingEntity> stale =
        repository.findAll().stream()
            .filter(
                entity ->
                    entity.getSource() == PricingSource.REMOTE
                        || entity.getSource() == PricingSource.FALLBACK)
            .toList();
    if (!stale.isEmpty()) {
      repository.deleteAllInBatch(stale);
      repository.flush();
    }
    persist(snapshots);
  }

  private void persist(List<PricingSnapshot> snapshots) {
    List<ModelPricingEntity> entities = new ArrayList<>(snapshots.size());
    for (PricingSnapshot snapshot : snapshots) {
      entities.add(toEntity(snapshot));
    }
    repository.saveAll(entities);
  }

  private static ModelPricingEntity toEntity(PricingSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    ModelPricing pricing = snapshot.pricing();
    return new ModelPricingEntity(
        pricing.provider().configKey(),
        pricing.model(),
        scale(pricing.inputTokenPricePerMillion()),
        scale(pricing.outputTokenPricePerMillion()),
        snapshot.source(),
        snapshot.fetchedAt(),
        snapshot.externalModelId(),
        null);
  }

  private static PricingSnapshot toSnapshot(ModelPricingEntity entity) {
    AiProvider provider =
        AiProvider.fromConfigKey(entity.getProvider())
            .orElseThrow(
                () ->
                    new PricingConfigurationException(
                        "unknown provider in model_pricing row: " + entity.getProvider()));
    ModelPricing pricing =
        new ModelPricing(
            provider,
            entity.getModel(),
            scale(entity.getInputPricePerMillion()),
            scale(entity.getOutputPricePerMillion()));
    return new PricingSnapshot(
        pricing, entity.getSource(), entity.getFetchedAt(), entity.getExternalModelId());
  }

  private static BigDecimal scale(BigDecimal value) {
    return value.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
  }
}
