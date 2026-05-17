package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshedEvent;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.pricing.JpaPricingSnapshotStore;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Read-time composite that merges the JPA snapshot store with the in-memory OVERRIDE layer. The
 * precedence is OVERRIDE > REMOTE > FALLBACK; FALLBACK rows live in the JPA store too (seeded by
 * {@code FallbackSeedRunner}), while OVERRIDE rows are never persisted.
 */
@Component
@Primary
public class CompositePricingProvider implements PricingProvider {

  private static final Comparator<PricingSnapshot> BY_PROVIDER_THEN_MODEL =
      Comparator.comparing((PricingSnapshot s) -> s.provider().configKey())
          .thenComparing(s -> s.model().toLowerCase(Locale.ROOT));

  private final JpaPricingSnapshotStore store;
  private final OverridesPricingLoader overrides;
  private final AtomicReference<List<PricingSnapshot>> cache = new AtomicReference<>();

  public CompositePricingProvider(JpaPricingSnapshotStore store, OverridesPricingLoader overrides) {
    this.store = store;
    this.overrides = overrides;
  }

  @Override
  public List<PricingSnapshot> snapshots() {
    List<PricingSnapshot> cached = cache.get();
    if (cached != null) {
      return cached;
    }
    List<PricingSnapshot> fresh = buildSnapshots();
    cache.compareAndSet(null, fresh);
    return cache.get();
  }

  @EventListener
  public void onPricingRefreshed(PricingRefreshedEvent event) {
    cache.set(null);
  }

  @Override
  public List<ModelPricing> all() {
    return snapshots().stream().map(PricingSnapshot::pricing).toList();
  }

  @Override
  public Optional<ModelPricing> find(AiProvider provider, String model) {
    if (provider == null || model == null || model.isBlank()) {
      return Optional.empty();
    }
    String target = key(provider, model);
    return snapshots().stream()
        .filter(snapshot -> key(snapshot.provider(), snapshot.model()).equals(target))
        .map(PricingSnapshot::pricing)
        .findFirst();
  }

  private List<PricingSnapshot> buildSnapshots() {
    Map<String, PricingSnapshot> merged = new LinkedHashMap<>();
    for (PricingSnapshot snapshot : store.findAll()) {
      merged.put(key(snapshot.provider(), snapshot.model()), snapshot);
    }
    for (PricingSnapshot snapshot : overrides.snapshots()) {
      merged.put(key(snapshot.provider(), snapshot.model()), snapshot);
    }
    return merged.values().stream().sorted(BY_PROVIDER_THEN_MODEL).toList();
  }

  private static String key(AiProvider provider, String model) {
    return provider.configKey() + ":" + model.trim().toLowerCase(Locale.ROOT);
  }
}
