package dev.diegobarrioh.tokenmeter.infrastructure.tokenizer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.knuddels.jtokkit.api.EncodingType;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingConfigurationException;
import dev.diegobarrioh.tokenmeter.application.tokenizer.TokenizationProfileCatalog;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenCounterStrategy;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenizationPrecision;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Eagerly loads {@code tokenizer-profiles.yaml} at startup into an ordered list of compiled {@link
 * ProfileEntry} records. Resolution is first-match-wins.
 *
 * <p>Mirrors the pattern of {@code PricingMappingLoader}: YAML ObjectMapper, fail-fast validation,
 * immutable state after construction. Implements {@link TokenizationProfileCatalog} so the
 * application layer depends only on the port (hexagonal layering respected).
 *
 * <p>Validation performed at startup (any failure throws {@link PricingConfigurationException}):
 *
 * <ul>
 *   <li>YAML file must exist and be parseable.
 *   <li>{@code provider} must resolve via {@link AiProvider#fromConfigKey}.
 *   <li>{@code model-pattern} must compile as a Java {@link Pattern}.
 *   <li>{@code strategy == JTOKKIT} requires {@code encoding} present and a valid {@link
 *       EncodingType} name.
 *   <li>{@code strategy == HEURISTIC} requires {@code heuristic-factor > 0}.
 *   <li>The {@code default} block must be present and valid.
 * </ul>
 */
@Component
public class TokenizerProfileLoader implements TokenizationProfileCatalog {

  private static final Logger LOG = LoggerFactory.getLogger(TokenizerProfileLoader.class);

  private static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final List<ProfileEntry> entries;
  private final ModelTokenizationProfile defaultProfile;

  /**
   * Sole constructor; Spring autowires it (single public constructor, no {@code @Autowired}
   * needed). Tests pass a {@link TokenizerProfileProperties} directly; {@code null} location
   * defaults to {@code classpath:tokenizer-profiles.yaml}.
   */
  public TokenizerProfileLoader(
      ResourceLoader resourceLoader, TokenizerProfileProperties properties) {
    Resource resource = resourceLoader.getResource(properties.profilesLocation());
    ProfilesConfiguration config = parseYaml(resource);
    this.entries = compileEntries(config.profiles());
    this.defaultProfile = buildDefaultProfile(config.defaultEntry());
    LOG.info("Loaded {} tokenizer profile entries", Integer.valueOf(this.entries.size()));
  }

  @Override
  public ModelTokenizationProfile resolve(AiProvider provider, String modelId) {
    if (provider == null || modelId == null) {
      return defaultProfile;
    }
    String normalizedModel = modelId.trim().toLowerCase(java.util.Locale.ROOT);
    for (ProfileEntry entry : entries) {
      if (entry.provider() == provider && entry.pattern().matcher(normalizedModel).matches()) {
        return entry.profile();
      }
    }
    return defaultProfile;
  }

  /** Returns the DEFAULT profile (never {@code null}). */
  public ModelTokenizationProfile defaultProfile() {
    return defaultProfile;
  }

  // --- private helpers ---

  private ProfilesConfiguration parseYaml(Resource resource) {
    if (resource == null || !resource.exists()) {
      throw new PricingConfigurationException("tokenizer-profiles.yaml not found");
    }
    try {
      ProfilesConfiguration config =
          YAML_MAPPER.readValue(resource.getInputStream(), ProfilesConfiguration.class);
      if (config == null) {
        throw new PricingConfigurationException("tokenizer-profiles.yaml is empty or unparseable");
      }
      return config;
    } catch (IOException e) {
      throw new PricingConfigurationException("Could not read tokenizer-profiles.yaml", e);
    }
  }

  private List<ProfileEntry> compileEntries(List<ProfileEntryDto> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return Collections.emptyList();
    }
    List<ProfileEntry> result = new ArrayList<>(dtos.size());
    for (int i = 0; i < dtos.size(); i++) {
      final int index = i;
      ProfileEntryDto dto = dtos.get(index);
      if (dto == null) {
        throw new PricingConfigurationException(
            "tokenizer-profiles.profiles[%d] is null".formatted(index));
      }
      AiProvider provider =
          AiProvider.fromConfigKey(dto.provider())
              .orElseThrow(
                  () ->
                      new PricingConfigurationException(
                          "Unknown provider at tokenizer-profiles.profiles[%d]: %s"
                              .formatted(index, dto.provider())));
      Pattern pattern = compilePattern(dto.modelPattern(), index);
      ModelTokenizationProfile profile = buildProfile(dto, index);
      result.add(new ProfileEntry(provider, pattern, profile));
    }
    return Collections.unmodifiableList(result);
  }

  private Pattern compilePattern(String modelPattern, int index) {
    if (modelPattern == null || modelPattern.isBlank()) {
      throw new PricingConfigurationException(
          "model-pattern is required at tokenizer-profiles.profiles[%d]".formatted(index));
    }
    try {
      return Pattern.compile(modelPattern, Pattern.CASE_INSENSITIVE);
    } catch (java.util.regex.PatternSyntaxException e) {
      throw new PricingConfigurationException(
          "Invalid model-pattern at tokenizer-profiles.profiles[%d]: %s"
              .formatted(index, modelPattern),
          e);
    }
  }

  private ModelTokenizationProfile buildProfile(ProfileEntryDto dto, int index) {
    if (dto.tokenizerId() == null || dto.tokenizerId().isBlank()) {
      throw new PricingConfigurationException(
          "tokenizer-id is required at tokenizer-profiles.profiles[%d]".formatted(index));
    }
    TokenizationPrecision precision = parsePrecision(dto.precision(), index);
    TokenCounterStrategy strategy = parseStrategy(dto.strategy(), index);
    validateEncodingOrFactor(dto, strategy, index);
    BigDecimal factor = dto.heuristicFactor() != null ? dto.heuristicFactor() : null;
    return new ModelTokenizationProfile(
        dto.tokenizerId(), precision, strategy, dto.encoding(), factor);
  }

  private ModelTokenizationProfile buildDefaultProfile(DefaultEntryDto dto) {
    if (dto == null) {
      throw new PricingConfigurationException("tokenizer-profiles.default block is required");
    }
    if (dto.tokenizerId() == null || dto.tokenizerId().isBlank()) {
      throw new PricingConfigurationException(
          "tokenizer-profiles.default.tokenizer-id is required");
    }
    TokenizationPrecision precision = parsePrecision(dto.precision(), -1);
    TokenCounterStrategy strategy = parseStrategy(dto.strategy(), -1);
    BigDecimal factor = dto.heuristicFactor() != null ? dto.heuristicFactor() : null;
    return new ModelTokenizationProfile(
        dto.tokenizerId(), precision, strategy, dto.encoding(), factor);
  }

  private TokenizationPrecision parsePrecision(String value, int index) {
    try {
      return TokenizationPrecision.valueOf(value);
    } catch (IllegalArgumentException | NullPointerException e) {
      String loc = index >= 0 ? "profiles[%d]".formatted(index) : "default";
      throw new PricingConfigurationException(
          "Invalid precision at tokenizer-profiles.%s: %s".formatted(loc, value));
    }
  }

  private TokenCounterStrategy parseStrategy(String value, int index) {
    try {
      return TokenCounterStrategy.valueOf(value);
    } catch (IllegalArgumentException | NullPointerException e) {
      String loc = index >= 0 ? "profiles[%d]".formatted(index) : "default";
      throw new PricingConfigurationException(
          "Invalid strategy at tokenizer-profiles.%s: %s".formatted(loc, value));
    }
  }

  private void validateEncodingOrFactor(
      ProfileEntryDto dto, TokenCounterStrategy strategy, int index) {
    if (strategy == TokenCounterStrategy.JTOKKIT) {
      if (dto.encoding() == null || dto.encoding().isBlank()) {
        throw new PricingConfigurationException(
            "encoding is required for JTOKKIT strategy at tokenizer-profiles.profiles[%d]"
                .formatted(index));
      }
      // Validate that the encoding name resolves to a known EncodingType
      try {
        EncodingType.valueOf(dto.encoding().toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new PricingConfigurationException(
            "Invalid encoding at tokenizer-profiles.profiles[%d]: %s"
                .formatted(index, dto.encoding()));
      }
    }
    if (strategy == TokenCounterStrategy.HEURISTIC) {
      if (dto.heuristicFactor() == null || dto.heuristicFactor().signum() <= 0) {
        throw new PricingConfigurationException(
            "heuristic-factor must be positive for HEURISTIC strategy at"
                + " tokenizer-profiles.profiles[%d]".formatted(index));
      }
    }
  }

  // --- internal DTOs and compiled entry ---

  private record ProfileEntry(
      AiProvider provider, Pattern pattern, ModelTokenizationProfile profile) {}

  private record ProfilesConfiguration(
      List<ProfileEntryDto> profiles, @JsonProperty("default") DefaultEntryDto defaultEntry) {}

  private record ProfileEntryDto(
      String provider,
      @JsonProperty("model-pattern") String modelPattern,
      @JsonProperty("tokenizer-id") String tokenizerId,
      String precision,
      String strategy,
      String encoding,
      @JsonProperty("heuristic-factor") BigDecimal heuristicFactor) {}

  private record DefaultEntryDto(
      @JsonProperty("tokenizer-id") String tokenizerId,
      String precision,
      String strategy,
      String encoding,
      @JsonProperty("heuristic-factor") BigDecimal heuristicFactor) {}
}
