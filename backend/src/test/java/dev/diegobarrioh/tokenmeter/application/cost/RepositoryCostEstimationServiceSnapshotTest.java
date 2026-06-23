package dev.diegobarrioh.tokenmeter.application.cost;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.application.tokenizer.ModelTokenizationProfileResolver;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.infrastructure.tokenizer.TokenizerProfileLoader;
import dev.diegobarrioh.tokenmeter.infrastructure.tokenizer.TokenizerProfileProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RepositoryCostEstimationServiceSnapshotTest {

  private static final OffsetDateTime FETCHED_AT =
      OffsetDateTime.of(2026, 5, 15, 3, 0, 0, 0, ZoneOffset.UTC);
  private static final long BASE_TOKENS = 100_000L;

  /**
   * Pricing-Input Determinism: feeding the same {@code ModelPricing} list to the cost service
   * produces identical estimates regardless of the {@link PricingSource} tag carried by the
   * surrounding snapshots. The service only sees {@code ModelPricing}, so the assertion is
   * "estimates from REMOTE-sourced prices == estimates from FALLBACK-sourced prices".
   */
  @ParameterizedTest
  @EnumSource(
      value = PricingSource.class,
      names = {"REMOTE", "FALLBACK"})
  void estimatesAreIdenticalAcrossSourcesForTheSamePriceList(PricingSource source) {
    List<ModelPricing> pricings = samplePricings();
    var service =
        new RepositoryCostEstimationService(providerFrom(pricings, source), defaultResolver());
    var control =
        new RepositoryCostEstimationService(
            providerFrom(pricings, PricingSource.REMOTE), defaultResolver());

    List<ModelCostEstimate> actual = service.estimate(BASE_TOKENS);
    List<ModelCostEstimate> expected = control.estimate(BASE_TOKENS);

    assertThat(actual)
        .as("Estimates must be element-wise equal regardless of source tag (%s)", source)
        .usingRecursiveComparison()
        .isEqualTo(expected);
  }

  private static List<ModelPricing> samplePricings() {
    return List.of(
        new ModelPricing(
            AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")),
        new ModelPricing(
            AiProvider.ANTHROPIC,
            "claude-opus-4-7",
            new BigDecimal("15.00"),
            new BigDecimal("75.00")),
        new ModelPricing(
            AiProvider.DEEPSEEK, "deepseek-chat", new BigDecimal("0.27"), new BigDecimal("1.10")));
  }

  private static ModelTokenizationProfileResolver defaultResolver() {
    return new ModelTokenizationProfileResolver(
        new TokenizerProfileLoader(
            new org.springframework.core.io.DefaultResourceLoader(),
            new TokenizerProfileProperties(null)));
  }

  private static PricingProvider providerFrom(List<ModelPricing> pricings, PricingSource source) {
    return new PricingProvider() {
      @Override
      public List<ModelPricing> all() {
        return pricings;
      }

      @Override
      public Optional<ModelPricing> find(AiProvider provider, String model) {
        return pricings.stream()
            .filter(p -> p.provider() == provider && p.model().equals(model))
            .findFirst();
      }

      @Override
      public List<PricingSnapshot> snapshots() {
        return pricings.stream()
            .map(p -> new PricingSnapshot(p, source, FETCHED_AT, null))
            .toList();
      }
    };
  }
}
