package dev.diegobarrioh.tokenmeter.application.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.application.tokenizer.ModelTokenizationProfileResolver;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.infrastructure.tokenizer.TokenizerProfileLoader;
import dev.diegobarrioh.tokenmeter.infrastructure.tokenizer.TokenizerProfileProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryCostEstimationServiceTest {
  @Test
  void estimatesRawAssistedAndAgenticCostsPerModel() {
    var service = new RepositoryCostEstimationService(pricingProvider(), defaultResolver());

    var estimates = service.estimate(1_000_000);

    assertThat(estimates).hasSize(3);
    assertThat(estimates)
        .extracting("mode")
        .containsExactly(
            CostEstimationMode.RAW, CostEstimationMode.ASSISTED, CostEstimationMode.AGENTIC);
    assertThat(estimates.get(0).estimatedInputTokens()).isZero();
    assertThat(estimates.get(0).estimatedOutputTokens()).isEqualTo(1_000_000);
    assertThat(estimates.get(0).totalCost()).isEqualByComparingTo("10.000000");
    assertThat(estimates.get(1).estimatedInputTokens()).isEqualTo(1_000_000);
    assertThat(estimates.get(1).estimatedOutputTokens()).isEqualTo(5_000_000);
    assertThat(estimates.get(1).totalCost()).isEqualByComparingTo("52.500000");
    assertThat(estimates.get(2).estimatedInputTokens()).isEqualTo(4_000_000);
    assertThat(estimates.get(2).estimatedOutputTokens()).isEqualTo(20_000_000);
    assertThat(estimates.get(2).totalCost()).isEqualByComparingTo("210.000000");
    assertThat(estimates.get(2).formula()).contains("baseTokens*4").contains("baseTokens*20");
  }

  @Test
  void rejectsNegativeBaseTokens() {
    var service = new RepositoryCostEstimationService(pricingProvider(), defaultResolver());

    assertThatThrownBy(() -> service.estimate(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  private static ModelTokenizationProfileResolver defaultResolver() {
    return new ModelTokenizationProfileResolver(
        new TokenizerProfileLoader(
            new org.springframework.core.io.DefaultResourceLoader(),
            new TokenizerProfileProperties(null)));
  }

  private static PricingProvider pricingProvider() {
    return new PricingProvider() {
      @Override
      public List<ModelPricing> all() {
        return List.of(
            new ModelPricing(
                AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")));
      }

      @Override
      public Optional<ModelPricing> find(AiProvider provider, String model) {
        return Optional.empty();
      }
    };
  }
}
