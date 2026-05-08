package dev.diegobarrioh.tokenmeter.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ModelPricingTest {
  @Test
  void createsPricingForSupportedProviderAndModel() {
    ModelPricing pricing =
        new ModelPricing(AiProvider.OPENAI, " gpt-4o ", BigDecimal.ONE, BigDecimal.TEN);

    assertThat(pricing.provider()).isEqualTo(AiProvider.OPENAI);
    assertThat(pricing.model()).isEqualTo("gpt-4o");
    assertThat(pricing.inputTokenPricePerMillion()).isEqualByComparingTo("1");
    assertThat(pricing.outputTokenPricePerMillion()).isEqualByComparingTo("10");
  }

  @Test
  void rejectsMissingModel() {
    assertThatThrownBy(
            () -> new ModelPricing(AiProvider.OPENAI, " ", BigDecimal.ONE, BigDecimal.TEN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("model is required");
  }

  @Test
  void rejectsNegativePricing() {
    assertThatThrownBy(
            () ->
                new ModelPricing(
                    AiProvider.OPENAI, "gpt-4o", BigDecimal.valueOf(-1), BigDecimal.TEN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("input token price");
  }

  @Test
  void parsesProviderKeysSafely() {
    assertThat(AiProvider.fromConfigKey("openai")).contains(AiProvider.OPENAI);
    assertThat(AiProvider.fromConfigKey("deepseek")).contains(AiProvider.DEEPSEEK);
    assertThat(AiProvider.fromConfigKey("unknown")).isEmpty();
  }
}
