package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingConfigurationException;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Eagerly loads {@code pricing-mapping.yaml} at startup into an immutable map keyed by the internal
 * {@code (provider, normalizedModel)} pair. Reuses the same normalization strategy as {@code
 * YamlPricingProvider#normalizeModel} (trim + lower-case) so internal lookups behave consistently
 * across the codebase.
 *
 * <p>Thread-safety: the underlying maps are computed once during construction and exposed through
 * {@link Collections#unmodifiableMap}. No hot-reload is supported.
 */
@Component
public class PricingMappingLoader {

  private static final Logger LOG = LoggerFactory.getLogger(PricingMappingLoader.class);
  private static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  private final Map<MappingKey, String> mappings;
  private final Map<String, MappingKey> reverseIndex;

  public PricingMappingLoader(ResourceLoader resourceLoader, PricingProperties properties) {
    Resource resource = resourceLoader.getResource(properties.mappingLocation());
    Map<MappingKey, String> loaded = load(resource);
    this.mappings = Collections.unmodifiableMap(loaded);
    LinkedHashMap<String, MappingKey> reverse = new LinkedHashMap<>();
    for (Map.Entry<MappingKey, String> entry : loaded.entrySet()) {
      reverse.put(entry.getValue(), entry.getKey());
    }
    this.reverseIndex = Collections.unmodifiableMap(reverse);
    LOG.info("Loaded {} pricing mapping entries", Integer.valueOf(this.mappings.size()));
  }

  /** Immutable mapping from internal {@code (provider, model)} pair to upstream LiteLLM key. */
  public Map<MappingKey, String> mappings() {
    return mappings;
  }

  /**
   * Reverse-lookup a configured internal key from an upstream LiteLLM key. Case-sensitive — the
   * upstream identifiers themselves are case-sensitive.
   */
  public Optional<MappingKey> reverseLookup(String litellmKey) {
    if (litellmKey == null || litellmKey.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(reverseIndex.get(litellmKey));
  }

  /** Normalizes a model identifier the same way {@code YamlPricingProvider} does. */
  static String normalizeModel(String model) {
    return model.trim().toLowerCase(Locale.ROOT);
  }

  private Map<MappingKey, String> load(Resource resource) {
    if (resource == null || !resource.exists()) {
      throw new PricingConfigurationException("pricing-mapping.yaml not found");
    }
    MappingConfiguration configuration;
    try {
      configuration = YAML_MAPPER.readValue(resource.getInputStream(), MappingConfiguration.class);
    } catch (IOException exception) {
      throw new PricingConfigurationException(
          "Could not read pricing mapping configuration", exception);
    }
    if (configuration == null || configuration.mapping() == null) {
      throw new PricingConfigurationException("pricing-mapping.mapping section is required");
    }
    List<MappingEntry> entries = configuration.mapping();
    if (entries.isEmpty()) {
      throw new PricingConfigurationException(
          "pricing-mapping.mapping must contain at least one entry");
    }

    LinkedHashMap<MappingKey, String> result = new LinkedHashMap<>();
    for (int idx = 0; idx < entries.size(); idx++) {
      final int index = idx;
      final MappingEntry entry = entries.get(index);
      if (entry == null) {
        throw new PricingConfigurationException(
            "pricing-mapping.mapping[%d] is required".formatted(index));
      }
      AiProvider provider =
          AiProvider.fromConfigKey(entry.provider())
              .orElseThrow(
                  () ->
                      new PricingConfigurationException(
                          "unknown provider at pricing-mapping.mapping[%d]: %s"
                              .formatted(index, entry.provider())));
      if (entry.model() == null || entry.model().isBlank()) {
        throw new PricingConfigurationException(
            "model is required at pricing-mapping.mapping[%d]".formatted(index));
      }
      if (entry.litellmKey() == null || entry.litellmKey().isBlank()) {
        throw new PricingConfigurationException(
            "litellm-key is required at pricing-mapping.mapping[%d]".formatted(index));
      }
      MappingKey key = new MappingKey(provider, normalizeModel(entry.model()));
      if (result.containsKey(key)) {
        throw new PricingConfigurationException(
            "duplicated mapping entry at pricing-mapping.mapping[%d]: %s:%s"
                .formatted(index, provider.configKey(), key.normalizedModel()));
      }
      result.put(key, entry.litellmKey());
    }
    return result;
  }

  /** Identifies a configured internal {@code (provider, normalizedModel)} pair. */
  public record MappingKey(AiProvider provider, String normalizedModel) {

    public MappingKey {
      if (provider == null) {
        throw new IllegalArgumentException("provider is required");
      }
      if (normalizedModel == null || normalizedModel.isBlank()) {
        throw new IllegalArgumentException("normalizedModel is required");
      }
    }
  }

  private record MappingConfiguration(List<MappingEntry> mapping) {}

  private record MappingEntry(
      String provider, String model, @JsonProperty("litellm-key") String litellmKey) {}
}
