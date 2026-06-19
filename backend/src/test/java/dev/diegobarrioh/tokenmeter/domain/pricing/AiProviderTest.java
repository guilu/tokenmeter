package dev.diegobarrioh.tokenmeter.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiProviderTest {

  @Test
  void exposesLowercaseConfigKeyForEveryValue() {
    assertThat(AiProvider.OPENAI.configKey()).isEqualTo("openai");
    assertThat(AiProvider.ANTHROPIC.configKey()).isEqualTo("anthropic");
    assertThat(AiProvider.GOOGLE.configKey()).isEqualTo("google");
    assertThat(AiProvider.DEEPSEEK.configKey()).isEqualTo("deepseek");
    assertThat(AiProvider.MISTRAL.configKey()).isEqualTo("mistral");
    assertThat(AiProvider.ALIBABA.configKey()).isEqualTo("alibaba");
    assertThat(AiProvider.XAI.configKey()).isEqualTo("xai");
  }

  @Test
  void fromConfigKeyResolvesNewlyAddedProviders() {
    assertThat(AiProvider.fromConfigKey("mistral")).contains(AiProvider.MISTRAL);
    assertThat(AiProvider.fromConfigKey("alibaba")).contains(AiProvider.ALIBABA);
    assertThat(AiProvider.fromConfigKey("xai")).contains(AiProvider.XAI);
  }

  @Test
  void fromConfigKeyStillResolvesLegacyKeys() {
    assertThat(AiProvider.fromConfigKey("openai")).contains(AiProvider.OPENAI);
    assertThat(AiProvider.fromConfigKey("anthropic")).contains(AiProvider.ANTHROPIC);
    assertThat(AiProvider.fromConfigKey("google")).contains(AiProvider.GOOGLE);
    assertThat(AiProvider.fromConfigKey("deepseek")).contains(AiProvider.DEEPSEEK);
  }

  @Test
  void fromConfigKeyIsTrimAndCaseInsensitive() {
    assertThat(AiProvider.fromConfigKey("  Mistral ")).contains(AiProvider.MISTRAL);
    assertThat(AiProvider.fromConfigKey("OPENAI")).contains(AiProvider.OPENAI);
  }

  @Test
  void fromConfigKeyReturnsEmptyForUnknownOrBlankInput() {
    assertThat(AiProvider.fromConfigKey("imaginary-ai")).isEmpty();
    assertThat(AiProvider.fromConfigKey("")).isEmpty();
    assertThat(AiProvider.fromConfigKey("   ")).isEmpty();
    assertThat(AiProvider.fromConfigKey(null)).isEmpty();
  }

  @Test
  void configKeyRoundTripsForEveryValue() {
    for (AiProvider provider : AiProvider.values()) {
      assertThat(AiProvider.fromConfigKey(provider.configKey())).contains(provider);
    }
  }

  @Test
  void fromLiteLlmProviderResolvesSupportedTokens() {
    assertThat(AiProvider.fromLiteLlmProvider("openai")).contains(AiProvider.OPENAI);
    assertThat(AiProvider.fromLiteLlmProvider("anthropic")).contains(AiProvider.ANTHROPIC);
    assertThat(AiProvider.fromLiteLlmProvider("gemini")).contains(AiProvider.GOOGLE);
    assertThat(AiProvider.fromLiteLlmProvider("deepseek")).contains(AiProvider.DEEPSEEK);
    assertThat(AiProvider.fromLiteLlmProvider("mistral")).contains(AiProvider.MISTRAL);
    assertThat(AiProvider.fromLiteLlmProvider("xai")).contains(AiProvider.XAI);
  }

  @Test
  void fromLiteLlmProviderIsTrimAndCaseInsensitive() {
    assertThat(AiProvider.fromLiteLlmProvider("  Gemini ")).contains(AiProvider.GOOGLE);
    assertThat(AiProvider.fromLiteLlmProvider("ANTHROPIC")).contains(AiProvider.ANTHROPIC);
  }

  @Test
  void fromLiteLlmProviderReturnsEmptyForUnsupportedOrBlankInput() {
    // Vertex / Qwen tokens are intentionally NOT auto-resolved (ambiguous variants); they only
    // enter via explicit pricing-mapping.yaml overrides.
    assertThat(AiProvider.fromLiteLlmProvider("vertex_ai-language-models")).isEmpty();
    assertThat(AiProvider.fromLiteLlmProvider("qwen")).isEmpty();
    assertThat(AiProvider.fromLiteLlmProvider("doc")).isEmpty();
    assertThat(AiProvider.fromLiteLlmProvider("")).isEmpty();
    assertThat(AiProvider.fromLiteLlmProvider("   ")).isEmpty();
    assertThat(AiProvider.fromLiteLlmProvider(null)).isEmpty();
  }
}
