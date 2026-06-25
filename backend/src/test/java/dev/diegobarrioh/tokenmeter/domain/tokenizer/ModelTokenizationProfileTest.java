package dev.diegobarrioh.tokenmeter.domain.tokenizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ModelTokenizationProfileTest {

  // --- Task 1.4 RED: HF_LOCAL enum value ---
  @Test
  void enumContainsHfLocal() {
    // Asserts HF_LOCAL is a valid constant; will be RED until task 1.5 adds it to the enum
    TokenCounterStrategy strategy = TokenCounterStrategy.valueOf("HF_LOCAL");
    assertThat(strategy).isNotNull();
  }

  @Test
  void rejectsBlankTokenizerId() {
    assertThatThrownBy(
            () ->
                new ModelTokenizationProfile(
                    "",
                    TokenizationPrecision.EXACT_LOCAL,
                    TokenCounterStrategy.JTOKKIT,
                    "O200K_BASE",
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tokenizerId");
  }

  @Test
  void rejectsNullTokenizerId() {
    assertThatThrownBy(
            () ->
                new ModelTokenizationProfile(
                    null,
                    TokenizationPrecision.EXACT_LOCAL,
                    TokenCounterStrategy.JTOKKIT,
                    "O200K_BASE",
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tokenizerId");
  }

  @Test
  void rejectsJtokkitProfileWithNullEncoding() {
    assertThatThrownBy(
            () ->
                new ModelTokenizationProfile(
                    "openai/o200k_base",
                    TokenizationPrecision.EXACT_LOCAL,
                    TokenCounterStrategy.JTOKKIT,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("encoding");
  }

  @Test
  void rejectsHeuristicProfileWithNullFactor() {
    assertThatThrownBy(
            () ->
                new ModelTokenizationProfile(
                    "anthropic/cl100k_heuristic",
                    TokenizationPrecision.HEURISTIC,
                    TokenCounterStrategy.HEURISTIC,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("heuristicFactor");
  }

  @Test
  void rejectsHeuristicProfileWithZeroFactor() {
    assertThatThrownBy(
            () ->
                new ModelTokenizationProfile(
                    "anthropic/cl100k_heuristic",
                    TokenizationPrecision.HEURISTIC,
                    TokenCounterStrategy.HEURISTIC,
                    null,
                    BigDecimal.ZERO,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("heuristicFactor");
  }

  @Test
  void acceptsValidJtokkitProfile() {
    ModelTokenizationProfile profile =
        new ModelTokenizationProfile(
            "openai/o200k_base",
            TokenizationPrecision.EXACT_LOCAL,
            TokenCounterStrategy.JTOKKIT,
            "O200K_BASE",
            null,
            null);

    assertThat(profile.tokenizerId()).isEqualTo("openai/o200k_base");
    assertThat(profile.precision()).isEqualTo(TokenizationPrecision.EXACT_LOCAL);
    assertThat(profile.strategy()).isEqualTo(TokenCounterStrategy.JTOKKIT);
    assertThat(profile.encoding()).isEqualTo("O200K_BASE");
    assertThat(profile.heuristicFactor()).isNull();
  }

  @Test
  void acceptsValidHeuristicProfile() {
    ModelTokenizationProfile profile =
        new ModelTokenizationProfile(
            "anthropic/cl100k_heuristic",
            TokenizationPrecision.HEURISTIC,
            TokenCounterStrategy.HEURISTIC,
            null,
            new BigDecimal("0.95"),
            null);

    assertThat(profile.tokenizerId()).isEqualTo("anthropic/cl100k_heuristic");
    assertThat(profile.precision()).isEqualTo(TokenizationPrecision.HEURISTIC);
    assertThat(profile.strategy()).isEqualTo(TokenCounterStrategy.HEURISTIC);
    assertThat(profile.encoding()).isNull();
    assertThat(profile.heuristicFactor()).isEqualByComparingTo("0.95");
  }

  // --- Task 1.6 RED: HF_LOCAL domain record tests (6-arg constructor, compile fails until 1.7) ---
  @Test
  void acceptsValidHfLocalProfile() {
    ModelTokenizationProfile profile =
        new ModelTokenizationProfile(
            "deepseek/tokenizer",
            TokenizationPrecision.EXACT_LOCAL,
            TokenCounterStrategy.HF_LOCAL,
            null,
            null,
            "deepseek/tokenizer.json");

    assertThat(profile.tokenizerId()).isEqualTo("deepseek/tokenizer");
    assertThat(profile.strategy()).isEqualTo(TokenCounterStrategy.HF_LOCAL);
    assertThat(profile.precision()).isEqualTo(TokenizationPrecision.EXACT_LOCAL);
    assertThat(profile.hfModelPath()).isEqualTo("deepseek/tokenizer.json");
  }

  @Test
  void rejectsHfLocalProfileWithNullPath() {
    assertThatThrownBy(
            () ->
                new ModelTokenizationProfile(
                    "deepseek/tokenizer",
                    TokenizationPrecision.EXACT_LOCAL,
                    TokenCounterStrategy.HF_LOCAL,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("hfModelPath");
  }

  @Test
  void rejectsHfLocalProfileWithBlankPath() {
    assertThatThrownBy(
            () ->
                new ModelTokenizationProfile(
                    "deepseek/tokenizer",
                    TokenizationPrecision.EXACT_LOCAL,
                    TokenCounterStrategy.HF_LOCAL,
                    null,
                    null,
                    "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("hfModelPath");
  }
}
