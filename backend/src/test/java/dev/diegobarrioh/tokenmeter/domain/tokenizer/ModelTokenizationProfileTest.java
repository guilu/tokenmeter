package dev.diegobarrioh.tokenmeter.domain.tokenizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ModelTokenizationProfileTest {

  @Test
  void rejectsBlankTokenizerId() {
    assertThatThrownBy(
            () ->
                new ModelTokenizationProfile(
                    "",
                    TokenizationPrecision.EXACT_LOCAL,
                    TokenCounterStrategy.JTOKKIT,
                    "O200K_BASE",
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
                    BigDecimal.ZERO))
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
            new BigDecimal("0.95"));

    assertThat(profile.tokenizerId()).isEqualTo("anthropic/cl100k_heuristic");
    assertThat(profile.precision()).isEqualTo(TokenizationPrecision.HEURISTIC);
    assertThat(profile.strategy()).isEqualTo(TokenCounterStrategy.HEURISTIC);
    assertThat(profile.encoding()).isNull();
    assertThat(profile.heuristicFactor()).isEqualByComparingTo("0.95");
  }
}
