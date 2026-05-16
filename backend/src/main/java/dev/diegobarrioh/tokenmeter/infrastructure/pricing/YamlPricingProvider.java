package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingConfigurationException;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
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
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * YAML-backed pricing source used for the cold-start fallback seed. Registered under the explicit
 * bean name {@code fallbackSeedSource}; {@code CompositePricingProvider} owns the {@code @Primary}
 * stereotype now.
 */
@Component("fallbackSeedSource")
public class YamlPricingProvider implements PricingProvider {
  private static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  private final List<ModelPricing> pricing;
  private final List<PricingSnapshot> snapshots;

  public YamlPricingProvider(
      @Value("${tokenmeter.pricing.config-location:classpath:pricing.yaml}") Resource resource) {
    List<ModelPricing> loaded = List.copyOf(load(resource));
    this.pricing = loaded;
    OffsetDateTime fetchedAt = OffsetDateTime.now(ZoneOffset.UTC);
    this.snapshots =
        loaded.stream()
            .map(p -> new PricingSnapshot(p, PricingSource.FALLBACK, fetchedAt, null))
            .toList();
  }

  @Override
  public List<ModelPricing> all() {
    return pricing;
  }

  @Override
  public Optional<ModelPricing> find(AiProvider provider, String model) {
    if (provider == null || model == null || model.isBlank()) {
      return Optional.empty();
    }
    String normalizedModel = normalizeModel(model);
    return pricing.stream()
        .filter(candidate -> candidate.provider() == provider)
        .filter(candidate -> normalizeModel(candidate.model()).equals(normalizedModel))
        .findFirst();
  }

  @Override
  public List<PricingSnapshot> snapshots() {
    return snapshots;
  }

  private List<ModelPricing> load(Resource resource) {
    if (resource == null || !resource.exists()) {
      throw new PricingConfigurationException("Pricing configuration resource does not exist");
    }
    PricingConfiguration configuration;
    try {
      configuration = YAML_MAPPER.readValue(resource.getInputStream(), PricingConfiguration.class);
    } catch (IOException exception) {
      throw new PricingConfigurationException("Could not read pricing configuration", exception);
    }
    if (configuration == null || configuration.pricing() == null) {
      throw new PricingConfigurationException("pricing section is required");
    }
    List<PricingEntry> models = configuration.pricing().models();
    if (models == null || models.isEmpty()) {
      throw new PricingConfigurationException("pricing.models must contain at least one model");
    }

    Set<String> uniqueKeys = new HashSet<>();
    List<ModelPricing> loaded = new ArrayList<>();
    for (int index = 0; index < models.size(); index++) {
      ModelPricing modelPricing = mapEntry(index, models.get(index));
      String key = modelPricing.provider().configKey() + ":" + normalizeModel(modelPricing.model());
      if (!uniqueKeys.add(key)) {
        throw new PricingConfigurationException("duplicated pricing entry: " + key);
      }
      loaded.add(modelPricing);
    }
    return loaded;
  }

  private ModelPricing mapEntry(int index, PricingEntry entry) {
    if (entry == null) {
      throw new PricingConfigurationException("pricing.models[%d] is required".formatted(index));
    }
    AiProvider provider =
        AiProvider.fromConfigKey(entry.provider())
            .orElseThrow(
                () ->
                    new PricingConfigurationException(
                        "unknown provider at pricing.models[%d]: %s"
                            .formatted(index, entry.provider())));
    try {
      return new ModelPricing(
          provider, entry.model(), entry.inputTokenPrice(), entry.outputTokenPrice());
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new PricingConfigurationException(
          "invalid pricing entry at pricing.models[%d]: %s"
              .formatted(index, exception.getMessage()),
          exception);
    }
  }

  private static String normalizeModel(String model) {
    return model.trim().toLowerCase(Locale.ROOT);
  }

  private record PricingConfiguration(PricingSection pricing) {}

  private record PricingSection(List<PricingEntry> models) {}

  private record PricingEntry(
      String provider,
      String model,
      @JsonProperty("input-token-price") BigDecimal inputTokenPrice,
      @JsonProperty("output-token-price") BigDecimal outputTokenPrice) {}
}
