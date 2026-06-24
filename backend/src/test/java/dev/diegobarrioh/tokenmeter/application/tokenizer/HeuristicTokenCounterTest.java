package dev.diegobarrioh.tokenmeter.application.tokenizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenCounterStrategy;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenizationPrecision;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HeuristicTokenCounterTest {

  private static ModelTokenizationProfile heuristicProfile(double factor) {
    return new ModelTokenizationProfile(
        "anthropic/cl100k_heuristic",
        TokenizationPrecision.HEURISTIC,
        TokenCounterStrategy.HEURISTIC,
        null,
        BigDecimal.valueOf(factor),
        null);
  }

  @Test
  void supportsHeuristicStrategy() {
    OpenAiTokenCounter reference = mock(OpenAiTokenCounter.class);
    HeuristicTokenCounter counter = new HeuristicTokenCounter(reference);

    ModelTokenizationProfile profile = heuristicProfile(0.95);
    assertThat(counter.supports(profile)).isTrue();
  }

  @Test
  void doesNotSupportJtokkitStrategy() {
    OpenAiTokenCounter reference = mock(OpenAiTokenCounter.class);
    HeuristicTokenCounter counter = new HeuristicTokenCounter(reference);

    ModelTokenizationProfile jtokkit =
        new ModelTokenizationProfile(
            "openai/o200k_base",
            TokenizationPrecision.EXACT_LOCAL,
            TokenCounterStrategy.JTOKKIT,
            "O200K_BASE",
            null,
            null);
    assertThat(counter.supports(jtokkit)).isFalse();
  }

  @Test
  void applysFactor095OverReferenceCount() {
    OpenAiTokenCounter reference = mock(OpenAiTokenCounter.class);
    when(reference.count("test text")).thenReturn(100L);

    HeuristicTokenCounter counter = new HeuristicTokenCounter(reference);
    long result = counter.count("test text", heuristicProfile(0.95));

    // Math.round(100 * 0.95) = Math.round(95.0) = 95
    assertThat(result).isEqualTo(95L);
  }

  @Test
  void factorOneReturnsReferenceCountUnchanged() {
    OpenAiTokenCounter reference = mock(OpenAiTokenCounter.class);
    when(reference.count("test text")).thenReturn(200L);

    HeuristicTokenCounter counter = new HeuristicTokenCounter(reference);
    long result = counter.count("test text", heuristicProfile(1.0));

    assertThat(result).isEqualTo(200L);
  }

  @Test
  void roundingIsHalfUp() {
    OpenAiTokenCounter reference = mock(OpenAiTokenCounter.class);
    // 10 * 0.95 = 9.5 → HALF_UP → 10
    when(reference.count("small")).thenReturn(10L);

    HeuristicTokenCounter counter = new HeuristicTokenCounter(reference);
    long result = counter.count("small", heuristicProfile(0.95));

    // BigDecimal HALF_UP: 9.5 → 10
    assertThat(result).isEqualTo(10L);
  }

  @Test
  void returnsZeroWhenReferenceCountIsZero() {
    OpenAiTokenCounter reference = mock(OpenAiTokenCounter.class);
    when(reference.count("")).thenReturn(0L);

    HeuristicTokenCounter counter = new HeuristicTokenCounter(reference);
    long result = counter.count("", heuristicProfile(0.95));

    assertThat(result).isZero();
  }

  @Test
  void factorZeroPointNinetyApplied() {
    OpenAiTokenCounter reference = mock(OpenAiTokenCounter.class);
    // 1000 * 0.90 = 900
    when(reference.count("gemini text")).thenReturn(1000L);

    HeuristicTokenCounter counter = new HeuristicTokenCounter(reference);
    ModelTokenizationProfile geminiProfile =
        new ModelTokenizationProfile(
            "google/gemini_heuristic",
            TokenizationPrecision.HEURISTIC,
            TokenCounterStrategy.HEURISTIC,
            null,
            new BigDecimal("0.90"),
            null);
    long result = counter.count("gemini text", geminiProfile);

    assertThat(result).isEqualTo(900L);
  }
}
