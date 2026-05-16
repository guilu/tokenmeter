package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.pricing.JpaPricingSnapshotStore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositePricingProviderTest {

  private static final OffsetDateTime REMOTE_AT =
      OffsetDateTime.of(2026, 5, 15, 3, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime FALLBACK_AT =
      OffsetDateTime.of(2026, 5, 14, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime OVERRIDE_AT =
      OffsetDateTime.of(2026, 5, 15, 4, 0, 0, 0, ZoneOffset.UTC);

  @Test
  void returnsStoreSnapshotsWhenNoOverridesAreConfigured() {
    JpaPricingSnapshotStore store = mock(JpaPricingSnapshotStore.class);
    OverridesPricingLoader overrides = mock(OverridesPricingLoader.class);
    when(store.findAll())
        .thenReturn(
            List.of(
                remote(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00"),
                remote(AiProvider.ANTHROPIC, "claude-opus-4-7", "15.00", "75.00"),
                remote(AiProvider.DEEPSEEK, "deepseek-chat", "0.27", "1.10")));
    when(overrides.snapshots()).thenReturn(List.of());
    CompositePricingProvider provider = new CompositePricingProvider(store, overrides);

    List<PricingSnapshot> snapshots = provider.snapshots();

    assertThat(snapshots)
        .extracting(s -> s.provider().configKey() + ":" + s.model())
        .containsExactly("anthropic:claude-opus-4-7", "deepseek:deepseek-chat", "openai:gpt-4o");
    assertThat(snapshots).allSatisfy(s -> assertThat(s.source()).isEqualTo(PricingSource.REMOTE));
  }

  @Test
  void overrideShadowsRemoteEntryWithSameProviderAndModel() {
    JpaPricingSnapshotStore store = mock(JpaPricingSnapshotStore.class);
    OverridesPricingLoader overrides = mock(OverridesPricingLoader.class);
    when(store.findAll())
        .thenReturn(
            List.of(
                remote(AiProvider.ANTHROPIC, "claude-opus-4-7", "15.00", "75.00"),
                remote(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00")));
    when(overrides.snapshots())
        .thenReturn(List.of(override(AiProvider.ANTHROPIC, "claude-opus-4-7", "12.00", "60.00")));
    CompositePricingProvider provider = new CompositePricingProvider(store, overrides);

    List<PricingSnapshot> snapshots = provider.snapshots();

    PricingSnapshot opus =
        snapshots.stream()
            .filter(
                s -> s.provider() == AiProvider.ANTHROPIC && s.model().equals("claude-opus-4-7"))
            .findFirst()
            .orElseThrow();
    assertThat(opus.source()).isEqualTo(PricingSource.OVERRIDE);
    assertThat(opus.pricing().inputTokenPricePerMillion()).isEqualByComparingTo("12.00");
    assertThat(opus.pricing().outputTokenPricePerMillion()).isEqualByComparingTo("60.00");

    PricingSnapshot gpt4o =
        snapshots.stream()
            .filter(s -> s.provider() == AiProvider.OPENAI && s.model().equals("gpt-4o"))
            .findFirst()
            .orElseThrow();
    assertThat(gpt4o.source()).isEqualTo(PricingSource.REMOTE);
    assertThat(snapshots).hasSize(2);
  }

  @Test
  void resultIsSortedByProviderConfigKeyThenModelLexicographically() {
    JpaPricingSnapshotStore store = mock(JpaPricingSnapshotStore.class);
    OverridesPricingLoader overrides = mock(OverridesPricingLoader.class);
    when(store.findAll())
        .thenReturn(
            List.of(
                remote(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00"),
                fallback(AiProvider.ALIBABA, "qwen3-max", "3.00", "12.00"),
                fallback(AiProvider.ANTHROPIC, "claude-sonnet-4-6", "3.00", "15.00"),
                remote(AiProvider.ANTHROPIC, "claude-opus-4-7", "15.00", "75.00")));
    when(overrides.snapshots()).thenReturn(List.of());
    CompositePricingProvider provider = new CompositePricingProvider(store, overrides);

    List<PricingSnapshot> snapshots = provider.snapshots();

    assertThat(snapshots)
        .extracting(s -> s.provider().configKey() + ":" + s.model())
        .containsExactly(
            "alibaba:qwen3-max",
            "anthropic:claude-opus-4-7",
            "anthropic:claude-sonnet-4-6",
            "openai:gpt-4o");
  }

  @Test
  void allReturnsModelPricingExtractedFromComposedSnapshots() {
    JpaPricingSnapshotStore store = mock(JpaPricingSnapshotStore.class);
    OverridesPricingLoader overrides = mock(OverridesPricingLoader.class);
    when(store.findAll()).thenReturn(List.of(remote(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00")));
    when(overrides.snapshots()).thenReturn(List.of());
    CompositePricingProvider provider = new CompositePricingProvider(store, overrides);

    List<ModelPricing> pricings = provider.all();

    assertThat(pricings).hasSize(1);
    assertThat(pricings.get(0).provider()).isEqualTo(AiProvider.OPENAI);
    assertThat(pricings.get(0).model()).isEqualTo("gpt-4o");
  }

  @Test
  void findResolvesMatchAndReturnsEmptyForUnknownModel() {
    JpaPricingSnapshotStore store = mock(JpaPricingSnapshotStore.class);
    OverridesPricingLoader overrides = mock(OverridesPricingLoader.class);
    when(store.findAll()).thenReturn(List.of(remote(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00")));
    when(overrides.snapshots()).thenReturn(List.of());
    CompositePricingProvider provider = new CompositePricingProvider(store, overrides);

    assertThat(provider.find(AiProvider.OPENAI, "gpt-4o")).isPresent();
    assertThat(provider.find(AiProvider.OPENAI, "unknown")).isEmpty();
    assertThat(provider.find(null, "gpt-4o")).isEmpty();
    assertThat(provider.find(AiProvider.OPENAI, "  ")).isEmpty();
  }

  private static PricingSnapshot remote(
      AiProvider provider, String model, String input, String output) {
    return new PricingSnapshot(
        new ModelPricing(provider, model, new BigDecimal(input), new BigDecimal(output)),
        PricingSource.REMOTE,
        REMOTE_AT,
        model);
  }

  private static PricingSnapshot fallback(
      AiProvider provider, String model, String input, String output) {
    return new PricingSnapshot(
        new ModelPricing(provider, model, new BigDecimal(input), new BigDecimal(output)),
        PricingSource.FALLBACK,
        FALLBACK_AT,
        null);
  }

  private static PricingSnapshot override(
      AiProvider provider, String model, String input, String output) {
    return new PricingSnapshot(
        new ModelPricing(provider, model, new BigDecimal(input), new BigDecimal(output)),
        PricingSource.OVERRIDE,
        OVERRIDE_AT,
        null);
  }
}
