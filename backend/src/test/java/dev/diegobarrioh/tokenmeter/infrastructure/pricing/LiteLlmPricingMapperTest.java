package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.LiteLlmPricingMapper.MappingResult;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.PricingMappingLoader.MappingKey;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.litellm.LiteLlmModelEntry;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiteLlmPricingMapperTest {

  private static final OffsetDateTime FETCHED_AT =
      OffsetDateTime.of(2026, 5, 15, 3, 0, 0, 0, ZoneOffset.UTC);

  @Test
  void importsEverySupportedUpstreamEntryNotJustMappedOnes() throws IOException {
    Map<String, LiteLlmModelEntry> raw = loadFixture();
    LiteLlmPricingMapper mapper = mapperWith(sampleMapping());

    MappingResult result = mapper.mapToSnapshots(raw, FETCHED_AT);
    List<PricingSnapshot> snapshots = result.snapshots();

    // Imported: gpt-4o, claude-opus-4-7, claude-haiku-4-5, deepseek-chat (override) and
    // mystery-model-unmapped (auto-discovered via litellm_provider=openai, absent from mapping).
    // Skipped: sample_spec (zero price) and null-price-row (null price).
    assertThat(snapshots).hasSize(5);
    assertThat(result.skipped()).isEqualTo(2);
    assertThat(snapshots)
        .allSatisfy(
            snapshot -> {
              assertThat(snapshot.source()).isEqualTo(PricingSource.REMOTE);
              assertThat(snapshot.fetchedAt()).isEqualTo(FETCHED_AT);
              assertThat(snapshot.externalModelId()).isNotBlank();
            });

    PricingSnapshot gpt4o = findSnapshot(snapshots, AiProvider.OPENAI, "gpt-4o");
    assertThat(gpt4o.pricing().inputTokenPricePerMillion()).isEqualByComparingTo("2.500000");
    assertThat(gpt4o.pricing().outputTokenPricePerMillion()).isEqualByComparingTo("10.000000");
    assertThat(gpt4o.externalModelId()).isEqualTo("gpt-4o");

    PricingSnapshot haiku = findSnapshot(snapshots, AiProvider.ANTHROPIC, "claude-haiku-4-5");
    assertThat(haiku.pricing().inputTokenPricePerMillion()).isEqualByComparingTo("0.800000");
  }

  @Test
  void autoDiscoversNewSupportedModelAbsentFromMapping() {
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put(
        "claude-opus-4-8",
        new LiteLlmModelEntry(
            new BigDecimal("0.000015"), new BigDecimal("0.000075"), "anthropic", null));
    // Empty mapping: nothing is whitelisted, yet the model must still be imported.
    LiteLlmPricingMapper mapper = mapperWith(Map.of());

    MappingResult result = mapper.mapToSnapshots(raw, FETCHED_AT);

    PricingSnapshot opus =
        findSnapshot(result.snapshots(), AiProvider.ANTHROPIC, "claude-opus-4-8");
    assertThat(opus.pricing().inputTokenPricePerMillion()).isEqualByComparingTo("15.000000");
    assertThat(opus.pricing().outputTokenPricePerMillion()).isEqualByComparingTo("75.000000");
    assertThat(opus.externalModelId()).isEqualTo("claude-opus-4-8");
    assertThat(result.skipped()).isZero();
  }

  @Test
  void usesConfiguredMappingAsOverrideForCanonicalModelName() {
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put(
        "deepseek/deepseek-chat",
        new LiteLlmModelEntry(
            new BigDecimal("0.00000027"), new BigDecimal("0.0000011"), "deepseek", null));
    Map<MappingKey, String> mappings = new LinkedHashMap<>();
    mappings.put(new MappingKey(AiProvider.DEEPSEEK, "deepseek-chat"), "deepseek/deepseek-chat");
    LiteLlmPricingMapper mapper = mapperWith(mappings);

    MappingResult result = mapper.mapToSnapshots(raw, FETCHED_AT);

    // Canonical model name comes from the override, not from the prefixed upstream key.
    PricingSnapshot snapshot =
        findSnapshot(result.snapshots(), AiProvider.DEEPSEEK, "deepseek-chat");
    assertThat(snapshot.externalModelId()).isEqualTo("deepseek/deepseek-chat");
  }

  @Test
  void derivesModelNameByStrippingProviderPrefixForUnmappedKeys() {
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put(
        "gemini/gemini-3-pro",
        new LiteLlmModelEntry(
            new BigDecimal("0.000001"), new BigDecimal("0.000005"), "gemini", null));
    LiteLlmPricingMapper mapper = mapperWith(Map.of());

    MappingResult result = mapper.mapToSnapshots(raw, FETCHED_AT);

    assertThat(findSnapshot(result.snapshots(), AiProvider.GOOGLE, "gemini-3-pro")).isNotNull();
  }

  @Test
  void skipsAndCountsUnsupportedProviderEntries() {
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put(
        "command-r-plus",
        new LiteLlmModelEntry(
            new BigDecimal("0.000003"), new BigDecimal("0.000015"), "cohere", null));
    LiteLlmPricingMapper mapper = mapperWith(Map.of());

    MappingResult result = mapper.mapToSnapshots(raw, FETCHED_AT);

    assertThat(result.snapshots()).isEmpty();
    assertThat(result.skipped()).isEqualTo(1);
  }

  @Test
  void skipsAndCountsMalformedPriceEntries() {
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put("null-row", new LiteLlmModelEntry(null, null, "openai", null));
    raw.put("zero-row", new LiteLlmModelEntry(BigDecimal.ZERO, BigDecimal.ZERO, "openai", null));
    LiteLlmPricingMapper mapper = mapperWith(Map.of());

    MappingResult result = mapper.mapToSnapshots(raw, FETCHED_AT);

    assertThat(result.snapshots()).isEmpty();
    assertThat(result.skipped()).isEqualTo(2);
  }

  @Test
  void dedupesEntriesThatNormalizeToTheSameProviderAndModel() {
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put(
        "gpt-4o",
        new LiteLlmModelEntry(
            new BigDecimal("0.0000025"), new BigDecimal("0.00001"), "openai", null));
    raw.put(
        "GPT-4o",
        new LiteLlmModelEntry(
            new BigDecimal("0.0000099"), new BigDecimal("0.00009"), "openai", null));
    LiteLlmPricingMapper mapper = mapperWith(Map.of());

    MappingResult result = mapper.mapToSnapshots(raw, FETCHED_AT);

    assertThat(result.snapshots()).hasSize(1);
    // First occurrence wins.
    assertThat(result.snapshots().get(0).pricing().inputTokenPricePerMillion())
        .isEqualByComparingTo("2.500000");
  }

  @Test
  void skipsNonCanonicalAutoDiscoveredVariants() {
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put(
        "gpt-4o-audio-preview",
        new LiteLlmModelEntry(
            new BigDecimal("0.0000025"), new BigDecimal("0.00001"), "openai", null));
    raw.put(
        "claude-opus-4-7-20260416",
        new LiteLlmModelEntry(
            new BigDecimal("0.000005"), new BigDecimal("0.000025"), "anthropic", null));
    raw.put(
        "ft:gpt-4o-2024-08-06",
        new LiteLlmModelEntry(
            new BigDecimal("0.00000375"), new BigDecimal("0.000015"), "openai", null));
    LiteLlmPricingMapper mapper = mapperWith(Map.of());

    MappingResult result = mapper.mapToSnapshots(raw, FETCHED_AT);

    assertThat(result.snapshots()).isEmpty();
    assertThat(result.skipped()).isEqualTo(3);
  }

  @Test
  void importsNonCanonicalKeyWhenExplicitlyOverridden() {
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put(
        "claude-opus-4-7-20260416",
        new LiteLlmModelEntry(
            new BigDecimal("0.000005"), new BigDecimal("0.000025"), "anthropic", null));
    Map<MappingKey, String> mappings = new LinkedHashMap<>();
    mappings.put(
        new MappingKey(AiProvider.ANTHROPIC, "claude-opus-4-7"), "claude-opus-4-7-20260416");
    LiteLlmPricingMapper mapper = mapperWith(mappings);

    MappingResult result = mapper.mapToSnapshots(raw, FETCHED_AT);

    // Override bypasses the canonical filter and pins the canonical model name.
    PricingSnapshot snapshot =
        findSnapshot(result.snapshots(), AiProvider.ANTHROPIC, "claude-opus-4-7");
    assertThat(snapshot.externalModelId()).isEqualTo("claude-opus-4-7-20260416");
    assertThat(result.skipped()).isZero();
  }

  @Test
  void skipsDeprecatedAutoDiscoveredModels() {
    // FETCHED_AT is 2026-05-15; this model was deprecated in the past.
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put(
        "gpt-old",
        new LiteLlmModelEntry(
            new BigDecimal("0.000001"), new BigDecimal("0.000005"), "openai", "2025-01-01"));
    LiteLlmPricingMapper mapper = mapperWith(Map.of());

    MappingResult result = mapper.mapToSnapshots(raw, FETCHED_AT);

    assertThat(result.snapshots()).isEmpty();
    assertThat(result.skipped()).isEqualTo(1);
  }

  @Test
  void importsModelsWithFutureOrAbsentDeprecationDate() {
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put(
        "gpt-future",
        new LiteLlmModelEntry(
            new BigDecimal("0.000001"), new BigDecimal("0.000005"), "openai", "2027-01-01"));
    raw.put(
        "gpt-active",
        new LiteLlmModelEntry(
            new BigDecimal("0.000002"), new BigDecimal("0.000006"), "openai", null));
    LiteLlmPricingMapper mapper = mapperWith(Map.of());

    MappingResult result = mapper.mapToSnapshots(raw, FETCHED_AT);

    assertThat(result.snapshots()).hasSize(2);
    assertThat(result.skipped()).isZero();
  }

  @Test
  void doesNotDropModelsWithUnparseableDeprecationDate() {
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put(
        "gpt-weird",
        new LiteLlmModelEntry(
            new BigDecimal("0.000001"), new BigDecimal("0.000005"), "openai", "soon"));
    LiteLlmPricingMapper mapper = mapperWith(Map.of());

    MappingResult result = mapper.mapToSnapshots(raw, FETCHED_AT);

    assertThat(result.snapshots()).hasSize(1);
    assertThat(result.skipped()).isZero();
  }

  @Test
  void importsDeprecatedModelWhenExplicitlyOverridden() {
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put(
        "gpt-old",
        new LiteLlmModelEntry(
            new BigDecimal("0.000001"), new BigDecimal("0.000005"), "openai", "2025-01-01"));
    Map<MappingKey, String> mappings = new LinkedHashMap<>();
    mappings.put(new MappingKey(AiProvider.OPENAI, "gpt-old"), "gpt-old");
    LiteLlmPricingMapper mapper = mapperWith(mappings);

    MappingResult result = mapper.mapToSnapshots(raw, FETCHED_AT);

    assertThat(findSnapshot(result.snapshots(), AiProvider.OPENAI, "gpt-old")).isNotNull();
    assertThat(result.skipped()).isZero();
  }

  @Test
  void rejectsNullRawPayload() {
    LiteLlmPricingMapper mapper = mapperWith(Map.of());

    assertThatThrownBy(() -> mapper.mapToSnapshots(null, FETCHED_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("raw payload is required");
  }

  @Test
  void rejectsNullFetchedAt() {
    LiteLlmPricingMapper mapper = mapperWith(Map.of());

    assertThatThrownBy(() -> mapper.mapToSnapshots(Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fetchedAt is required");
  }

  private static LiteLlmPricingMapper mapperWith(Map<MappingKey, String> mappings) {
    PricingMappingLoader mappingLoader = mock(PricingMappingLoader.class);
    when(mappingLoader.mappings()).thenReturn(mappings);
    return new LiteLlmPricingMapper(mappingLoader);
  }

  private static PricingSnapshot findSnapshot(
      List<PricingSnapshot> snapshots, AiProvider provider, String model) {
    return snapshots.stream()
        .filter(s -> s.provider() == provider && s.model().equals(model))
        .findFirst()
        .orElseThrow(() -> new AssertionError("snapshot not found: " + provider + ":" + model));
  }

  private static Map<MappingKey, String> sampleMapping() {
    Map<MappingKey, String> mappings = new LinkedHashMap<>();
    mappings.put(new MappingKey(AiProvider.OPENAI, "gpt-4o"), "gpt-4o");
    mappings.put(new MappingKey(AiProvider.ANTHROPIC, "claude-opus-4-7"), "claude-opus-4-7");
    mappings.put(new MappingKey(AiProvider.ANTHROPIC, "claude-haiku-4-5"), "claude-haiku-4-5");
    mappings.put(new MappingKey(AiProvider.DEEPSEEK, "deepseek-chat"), "deepseek/deepseek-chat");
    // present in mapping but absent upstream => simply not imported (no longer counted as skipped).
    mappings.put(new MappingKey(AiProvider.ANTHROPIC, "claude-sonnet-4-6"), "claude-sonnet-4-6");
    return mappings;
  }

  private static Map<String, LiteLlmModelEntry> loadFixture() throws IOException {
    try (InputStream stream =
        LiteLlmPricingMapperTest.class.getResourceAsStream("/fixtures/litellm-sample.json")) {
      if (stream == null) {
        throw new IllegalStateException("fixture /fixtures/litellm-sample.json not found");
      }
      ObjectMapper jackson = new ObjectMapper();
      return jackson.readValue(stream, new TypeReference<Map<String, LiteLlmModelEntry>>() {});
    }
  }
}
