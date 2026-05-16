package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingConfigurationException;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Reads {@code pricing-overrides.yaml} (optional) at startup and exposes its entries as {@link
 * PricingSnapshot} values tagged with {@link PricingSource#OVERRIDE}. The file is intentionally
 * optional: if missing or empty, this loader returns an empty list and {@code
 * CompositePricingProvider} falls back to REMOTE / FALLBACK rows only.
 */
@Component
public class OverridesPricingLoader {

  private static final Logger LOG = LoggerFactory.getLogger(OverridesPricingLoader.class);
  private static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  private final List<PricingSnapshot> snapshots;

  public OverridesPricingLoader(ResourceLoader resourceLoader, PricingProperties properties) {
    Resource resource = resourceLoader.getResource(properties.overridesLocation());
    this.snapshots = List.copyOf(load(resource));
  }

  /** Immutable snapshot list. Empty when no overrides file is present. */
  public List<PricingSnapshot> snapshots() {
    return snapshots;
  }

  private List<PricingSnapshot> load(Resource resource) {
    if (resource == null || !resource.exists()) {
      LOG.debug("Pricing overrides resource not present; no OVERRIDE snapshots will be applied");
      return List.of();
    }
    OverridesConfiguration configuration;
    try {
      configuration =
          YAML_MAPPER.readValue(resource.getInputStream(), OverridesConfiguration.class);
    } catch (IOException exception) {
      throw new PricingConfigurationException(
          "Could not read pricing overrides configuration", exception);
    }
    if (configuration == null || configuration.overrides() == null) {
      LOG.info("Pricing overrides file present but empty; no OVERRIDE snapshots will be applied");
      return List.of();
    }
    List<OverrideEntry> entries = configuration.overrides();
    if (entries.isEmpty()) {
      LOG.info("Pricing overrides file present but empty; no OVERRIDE snapshots will be applied");
      return List.of();
    }

    OffsetDateTime fetchedAt = OffsetDateTime.now(ZoneOffset.UTC);
    Set<String> uniqueKeys = new HashSet<>();
    List<PricingSnapshot> loaded = new ArrayList<>();
    for (int index = 0; index < entries.size(); index++) {
      PricingSnapshot snapshot = mapEntry(index, entries.get(index), fetchedAt);
      String key =
          snapshot.provider().configKey() + ":" + snapshot.model().toLowerCase(Locale.ROOT);
      if (!uniqueKeys.add(key)) {
        throw new PricingConfigurationException("duplicated override entry: " + key);
      }
      loaded.add(snapshot);
    }
    LOG.info("Loaded {} pricing override entries", loaded.size());
    return loaded;
  }

  private PricingSnapshot mapEntry(int index, OverrideEntry entry, OffsetDateTime fetchedAt) {
    if (entry == null) {
      throw new PricingConfigurationException("pricing-overrides[%d] is required".formatted(index));
    }
    AiProvider provider =
        AiProvider.fromConfigKey(entry.provider())
            .orElseThrow(
                () ->
                    new PricingConfigurationException(
                        "unknown provider at overrides[%d]: %s"
                            .formatted(index, entry.provider())));
    try {
      ModelPricing pricing =
          new ModelPricing(
              provider, entry.model(), entry.inputTokenPrice(), entry.outputTokenPrice());
      return new PricingSnapshot(pricing, PricingSource.OVERRIDE, fetchedAt, null);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new PricingConfigurationException(
          "invalid override entry at overrides[%d]: %s".formatted(index, exception.getMessage()),
          exception);
    }
  }

  private record OverridesConfiguration(List<OverrideEntry> overrides) {}

  private record OverrideEntry(
      String provider,
      String model,
      @JsonProperty("input-token-price") BigDecimal inputTokenPrice,
      @JsonProperty("output-token-price") BigDecimal outputTokenPrice) {}
}
