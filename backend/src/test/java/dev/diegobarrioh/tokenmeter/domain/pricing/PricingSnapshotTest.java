package dev.diegobarrioh.tokenmeter.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PricingSnapshotTest {

  private static final OffsetDateTime FETCHED_AT =
      OffsetDateTime.of(2026, 5, 15, 3, 0, 0, 0, ZoneOffset.UTC);

  @Test
  void rejectsNullPricing() {
    assertThatThrownBy(
            () -> new PricingSnapshot(null, PricingSource.FALLBACK, FETCHED_AT, "external-id"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("pricing");
  }

  @Test
  void rejectsNullSource() {
    ModelPricing pricing = samplePricing();
    assertThatThrownBy(() -> new PricingSnapshot(pricing, null, FETCHED_AT, "external-id"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("source");
  }

  @Test
  void rejectsNullFetchedAt() {
    ModelPricing pricing = samplePricing();
    assertThatThrownBy(() -> new PricingSnapshot(pricing, PricingSource.REMOTE, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fetchedAt");
  }

  @Test
  void allowsNullExternalModelIdForFallbackAndOverride() {
    PricingSnapshot fallback =
        new PricingSnapshot(samplePricing(), PricingSource.FALLBACK, FETCHED_AT, null);
    PricingSnapshot override =
        new PricingSnapshot(samplePricing(), PricingSource.OVERRIDE, FETCHED_AT, null);

    assertThat(fallback.externalModelId()).isNull();
    assertThat(override.externalModelId()).isNull();
  }

  @Test
  void delegatesProviderAndModelToWrappedPricing() {
    ModelPricing pricing = samplePricing();
    PricingSnapshot snapshot =
        new PricingSnapshot(pricing, PricingSource.REMOTE, FETCHED_AT, "gpt-4o");

    assertThat(snapshot.provider()).isEqualTo(AiProvider.OPENAI);
    assertThat(snapshot.model()).isEqualTo("gpt-4o");
    assertThat(snapshot.pricing()).isSameAs(pricing);
    assertThat(snapshot.source()).isEqualTo(PricingSource.REMOTE);
    assertThat(snapshot.fetchedAt()).isEqualTo(FETCHED_AT);
    assertThat(snapshot.externalModelId()).isEqualTo("gpt-4o");
  }

  private static ModelPricing samplePricing() {
    return new ModelPricing(
        AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00"));
  }
}
