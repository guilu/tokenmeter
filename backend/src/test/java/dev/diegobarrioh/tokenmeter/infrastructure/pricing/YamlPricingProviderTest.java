package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingConfigurationException;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingNotFoundException;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class YamlPricingProviderTest {
  @Test
  void loadsValidPricingConfiguration() {
    YamlPricingProvider provider = provider(validPricingYaml());

    assertThat(provider.all()).hasSize(4);
    assertThat(provider.find(AiProvider.OPENAI, "GPT-4O"))
        .isPresent()
        .get()
        .satisfies(
            pricing -> {
              assertThat(pricing.model()).isEqualTo("gpt-4o");
              assertThat(pricing.inputTokenPricePerMillion()).isEqualByComparingTo("2.50");
              assertThat(pricing.outputTokenPricePerMillion()).isEqualByComparingTo("10.00");
            });
  }

  @Test
  void rejectsMalformedYamlSafely() {
    assertThatThrownBy(() -> provider("pricing: ["))
        .isInstanceOf(PricingConfigurationException.class)
        .hasMessageContaining("Could not read pricing configuration");
  }

  @Test
  void rejectsMissingRequiredFields() {
    assertThatThrownBy(
            () ->
                provider(
                    """
                    pricing:
                      models:
                        - provider: openai
                          model: gpt-4o
                          output-token-price: 10.00
                    """))
        .isInstanceOf(PricingConfigurationException.class)
        .hasMessageContaining("input token price is required");
  }

  @Test
  void rejectsUnknownProviderInConfiguration() {
    assertThatThrownBy(
            () ->
                provider(
                    """
                    pricing:
                      models:
                        - provider: imaginary-ai
                          model: foo
                          input-token-price: 1.00
                          output-token-price: 2.00
                    """))
        .isInstanceOf(PricingConfigurationException.class)
        .hasMessageContaining("unknown provider");
  }

  @Test
  void returnsEmptyForUnknownModel() {
    YamlPricingProvider provider = provider(validPricingYaml());

    assertThat(provider.find(AiProvider.OPENAI, "unknown-model")).isEmpty();
    assertThatThrownBy(() -> provider.require(AiProvider.OPENAI, "unknown-model"))
        .isInstanceOf(PricingNotFoundException.class)
        .hasMessageContaining("Pricing not found");
  }

  @Test
  void loadsPricingConfigurationQuickly() {
    assertTimeoutPreemptively(
        Duration.ofMillis(250),
        () -> {
          YamlPricingProvider provider = provider(validPricingYaml());
          assertThat(provider.all()).hasSize(4);
        });
  }

  private static YamlPricingProvider provider(String yaml) {
    return new YamlPricingProvider(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
  }

  private static String validPricingYaml() {
    return """
        pricing:
          models:
            - provider: openai
              model: gpt-4o
              input-token-price: 2.50
              output-token-price: 10.00
            - provider: anthropic
              model: claude-3-5-sonnet
              input-token-price: 3.00
              output-token-price: 15.00
            - provider: google
              model: gemini-1.5-pro
              input-token-price: 1.25
              output-token-price: 5.00
            - provider: deepseek
              model: deepseek-chat
              input-token-price: 0.27
              output-token-price: 1.10
        """;
  }
}
