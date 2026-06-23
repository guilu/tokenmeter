package dev.diegobarrioh.tokenmeter.application.cost;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.application.tokenizer.ModelTokenizationProfileResolver;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.infrastructure.tokenizer.TokenizerProfileLoader;
import dev.diegobarrioh.tokenmeter.infrastructure.tokenizer.TokenizerProfileProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RepositoryCostEstimationServiceRefreshTest {

  /**
   * Spec scenario "Refresh between estimations is observed": the service iterates {@code
   * PricingProvider.all()} each call, so a snapshot swap between calls is reflected in the very
   * next invocation.
   */
  @Test
  void secondEstimateReflectsUpdatedPricesPublishedBetweenCalls() {
    List<ModelPricing> first =
        List.of(
            new ModelPricing(
                AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")));
    List<ModelPricing> second =
        List.of(
            new ModelPricing(
                AiProvider.OPENAI, "gpt-4o", new BigDecimal("5.00"), new BigDecimal("20.00")));
    SwappablePricingProvider provider = new SwappablePricingProvider(List.of(first, second));
    var service = new RepositoryCostEstimationService(provider, defaultResolver());

    List<ModelCostEstimate> beforeRefresh = service.estimate(1_000_000L);
    List<ModelCostEstimate> afterRefresh = service.estimate(1_000_000L);

    ModelCostEstimate rawBefore = pick(beforeRefresh, CostEstimationMode.RAW);
    ModelCostEstimate rawAfter = pick(afterRefresh, CostEstimationMode.RAW);
    assertThat(rawBefore.totalCost()).isEqualByComparingTo("10.000000");
    assertThat(rawAfter.totalCost()).isEqualByComparingTo("20.000000");
    assertThat(rawAfter.totalCost()).isNotEqualByComparingTo(rawBefore.totalCost());
  }

  /**
   * Spec scenario "Mid-analysis consistency": a single {@code estimate(...)} call must use one
   * snapshot throughout. Because the service flat-maps over the provider's {@code all()} once per
   * invocation, this is structurally guaranteed; we verify the provider is invoked exactly once per
   * estimation call.
   */
  @Test
  void singleEstimationCallObservesSnapshotExactlyOnce() {
    List<ModelPricing> pricings =
        List.of(
            new ModelPricing(
                AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")),
            new ModelPricing(
                AiProvider.ANTHROPIC,
                "claude-opus-4-7",
                new BigDecimal("15.00"),
                new BigDecimal("75.00")));
    CountingPricingProvider provider = new CountingPricingProvider(pricings);
    var service = new RepositoryCostEstimationService(provider, defaultResolver());

    service.estimate(1_000_000L);

    assertThat(provider.invocationCount()).isEqualTo(1);
  }

  private static ModelTokenizationProfileResolver defaultResolver() {
    return new ModelTokenizationProfileResolver(
        new TokenizerProfileLoader(
            new org.springframework.core.io.DefaultResourceLoader(),
            new TokenizerProfileProperties(null)));
  }

  private static ModelCostEstimate pick(
      List<ModelCostEstimate> estimates, CostEstimationMode mode) {
    return estimates.stream()
        .filter(e -> e.mode() == mode)
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing mode " + mode));
  }

  private static final class SwappablePricingProvider implements PricingProvider {
    private final List<List<ModelPricing>> snapshots;
    private final AtomicInteger index = new AtomicInteger(0);

    SwappablePricingProvider(List<List<ModelPricing>> snapshots) {
      this.snapshots = snapshots;
    }

    @Override
    public List<ModelPricing> all() {
      int currentIndex = Math.min(index.getAndIncrement(), snapshots.size() - 1);
      return snapshots.get(currentIndex);
    }

    @Override
    public Optional<ModelPricing> find(AiProvider provider, String model) {
      return Optional.empty();
    }
  }

  private static final class CountingPricingProvider implements PricingProvider {
    private final List<ModelPricing> pricings;
    private final AtomicInteger invocations = new AtomicInteger(0);

    CountingPricingProvider(List<ModelPricing> pricings) {
      this.pricings = pricings;
    }

    @Override
    public List<ModelPricing> all() {
      invocations.incrementAndGet();
      return pricings;
    }

    @Override
    public Optional<ModelPricing> find(AiProvider provider, String model) {
      return Optional.empty();
    }

    int invocationCount() {
      return invocations.get();
    }
  }
}
