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
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.PricingMappingLoader.MappingKey;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.litellm.LiteLlmModelEntry;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiteLlmPricingMapperTest {

  private static final OffsetDateTime FETCHED_AT =
      OffsetDateTime.of(2026, 5, 15, 3, 0, 0, 0, ZoneOffset.UTC);

  @Test
  void translatesUpstreamCatalogueIntoRemoteSnapshots() throws IOException {
    Map<String, LiteLlmModelEntry> raw = loadFixture();
    PricingMappingLoader mappingLoader = mock(PricingMappingLoader.class);
    when(mappingLoader.mappings()).thenReturn(sampleMapping());
    LiteLlmPricingMapper mapper = new LiteLlmPricingMapper(mappingLoader);

    var snapshots = mapper.mapToSnapshots(raw, FETCHED_AT);

    // mystery-model-unmapped has no mapping entry => not surfaced.
    // null-price-row has no mapping entry => not surfaced.
    // sample_spec is never looked up because it is not in the mapping.
    // claude-sonnet-4-6 IS in the mapping but absent upstream => skipped with WARN.
    assertThat(snapshots).hasSize(4);
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

    PricingSnapshot opus = findSnapshot(snapshots, AiProvider.ANTHROPIC, "claude-opus-4-7");
    assertThat(opus.pricing().inputTokenPricePerMillion()).isEqualByComparingTo("15.000000");
    assertThat(opus.pricing().outputTokenPricePerMillion()).isEqualByComparingTo("75.000000");

    PricingSnapshot haiku = findSnapshot(snapshots, AiProvider.ANTHROPIC, "claude-haiku-4-5");
    assertThat(haiku.pricing().inputTokenPricePerMillion()).isEqualByComparingTo("0.800000");
    assertThat(haiku.pricing().outputTokenPricePerMillion()).isEqualByComparingTo("4.000000");

    PricingSnapshot deepseek = findSnapshot(snapshots, AiProvider.DEEPSEEK, "deepseek-chat");
    assertThat(deepseek.pricing().inputTokenPricePerMillion()).isEqualByComparingTo("0.270000");
    assertThat(deepseek.pricing().outputTokenPricePerMillion()).isEqualByComparingTo("1.100000");
  }

  @Test
  void skipsMappedEntriesWithNullPrices() {
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put("gpt-4o", new LiteLlmModelEntry(null, null, "openai", null));
    PricingMappingLoader mappingLoader = mock(PricingMappingLoader.class);
    Map<MappingKey, String> mappings = new LinkedHashMap<>();
    mappings.put(new MappingKey(AiProvider.OPENAI, "gpt-4o"), "gpt-4o");
    when(mappingLoader.mappings()).thenReturn(mappings);
    LiteLlmPricingMapper mapper = new LiteLlmPricingMapper(mappingLoader);

    var snapshots = mapper.mapToSnapshots(raw, FETCHED_AT);

    assertThat(snapshots).isEmpty();
  }

  @Test
  void skipsMappedEntriesWithZeroPrices() {
    Map<String, LiteLlmModelEntry> raw = new LinkedHashMap<>();
    raw.put(
        "gpt-4o",
        new LiteLlmModelEntry(
            java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, "openai", null));
    PricingMappingLoader mappingLoader = mock(PricingMappingLoader.class);
    Map<MappingKey, String> mappings = new LinkedHashMap<>();
    mappings.put(new MappingKey(AiProvider.OPENAI, "gpt-4o"), "gpt-4o");
    when(mappingLoader.mappings()).thenReturn(mappings);
    LiteLlmPricingMapper mapper = new LiteLlmPricingMapper(mappingLoader);

    assertThat(mapper.mapToSnapshots(raw, FETCHED_AT)).isEmpty();
  }

  @Test
  void rejectsNullRawPayload() {
    PricingMappingLoader mappingLoader = mock(PricingMappingLoader.class);
    when(mappingLoader.mappings()).thenReturn(Map.of());
    LiteLlmPricingMapper mapper = new LiteLlmPricingMapper(mappingLoader);

    assertThatThrownBy(() -> mapper.mapToSnapshots(null, FETCHED_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("raw payload is required");
  }

  @Test
  void rejectsNullFetchedAt() {
    PricingMappingLoader mappingLoader = mock(PricingMappingLoader.class);
    when(mappingLoader.mappings()).thenReturn(Map.of());
    LiteLlmPricingMapper mapper = new LiteLlmPricingMapper(mappingLoader);

    assertThatThrownBy(() -> mapper.mapToSnapshots(Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fetchedAt is required");
  }

  private static PricingSnapshot findSnapshot(
      java.util.List<PricingSnapshot> snapshots, AiProvider provider, String model) {
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
    // present in mapping but absent upstream => triggers the WARN-skip path.
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
