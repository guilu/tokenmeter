package dev.diegobarrioh.tokenmeter.application.tokenizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenCounterStrategy;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenizationPrecision;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenCounterRegistryTest {

  private static ModelTokenizationProfile jtokkitProfile() {
    return new ModelTokenizationProfile(
        "openai/o200k_base",
        TokenizationPrecision.EXACT_LOCAL,
        TokenCounterStrategy.JTOKKIT,
        "O200K_BASE",
        null,
        null);
  }

  private static ModelTokenizationProfile heuristicProfile() {
    return new ModelTokenizationProfile(
        "anthropic/cl100k_heuristic",
        TokenizationPrecision.HEURISTIC,
        TokenCounterStrategy.HEURISTIC,
        null,
        new BigDecimal("0.95"),
        null);
  }

  @Test
  void resolvesJtokkitProfileToJtokkitCounter() {
    JtokkitTokenCounter jtokkit = new JtokkitTokenCounter();
    OpenAiTokenCounter openAi = new OpenAiTokenCounter();
    HeuristicTokenCounter heuristic = new HeuristicTokenCounter(openAi);
    TokenCounterRegistry registry = new TokenCounterRegistry(List.of(jtokkit, heuristic));

    TokenCounter resolved = registry.resolve(jtokkitProfile());

    assertThat(resolved).isInstanceOf(JtokkitTokenCounter.class);
  }

  @Test
  void resolvesHeuristicProfileToHeuristicCounter() {
    JtokkitTokenCounter jtokkit = new JtokkitTokenCounter();
    OpenAiTokenCounter openAi = new OpenAiTokenCounter();
    HeuristicTokenCounter heuristic = new HeuristicTokenCounter(openAi);
    TokenCounterRegistry registry = new TokenCounterRegistry(List.of(jtokkit, heuristic));

    TokenCounter resolved = registry.resolve(heuristicProfile());

    assertThat(resolved).isInstanceOf(HeuristicTokenCounter.class);
  }

  @Test
  void throwsIllegalStateWhenNoCounterSupportsProfile() {
    // Empty registry — no counter supports anything
    TokenCounterRegistry registry = new TokenCounterRegistry(List.of());

    assertThatThrownBy(() -> registry.resolve(jtokkitProfile()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("openai/o200k_base");
  }
}
