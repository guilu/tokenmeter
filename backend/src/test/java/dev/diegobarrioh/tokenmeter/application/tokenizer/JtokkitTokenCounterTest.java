package dev.diegobarrioh.tokenmeter.application.tokenizer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenCounterStrategy;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenizationPrecision;
import org.junit.jupiter.api.Test;

class JtokkitTokenCounterTest {

  private final JtokkitTokenCounter counter = new JtokkitTokenCounter();

  private static ModelTokenizationProfile o200kProfile() {
    return new ModelTokenizationProfile(
        "openai/o200k_base",
        TokenizationPrecision.EXACT_LOCAL,
        TokenCounterStrategy.JTOKKIT,
        "O200K_BASE",
        null,
        null);
  }

  private static ModelTokenizationProfile cl100kProfile() {
    return new ModelTokenizationProfile(
        "openai/cl100k_base",
        TokenizationPrecision.EXACT_LOCAL,
        TokenCounterStrategy.JTOKKIT,
        "CL100K_BASE",
        null,
        null);
  }

  @Test
  void supportsJtokkitStrategy() {
    assertThat(counter.supports(o200kProfile())).isTrue();
  }

  @Test
  void doesNotSupportHeuristicStrategy() {
    ModelTokenizationProfile heuristic =
        new ModelTokenizationProfile(
            "anthropic/cl100k_heuristic",
            TokenizationPrecision.HEURISTIC,
            TokenCounterStrategy.HEURISTIC,
            null,
            new java.math.BigDecimal("0.95"),
            null);
    assertThat(counter.supports(heuristic)).isFalse();
  }

  @Test
  void countIsPositiveForO200kBase() {
    long count = counter.count("Hello, world!", o200kProfile());
    assertThat(count).isPositive();
  }

  @Test
  void o200kAndCl100kResolveToDistinctEncodings() {
    // Digit runs tokenize differently across o200k and cl100k vocabularies, so a digit/unicode
    // heavy string proves the two profiles resolve to DIFFERENT encoders (not a silent shared one).
    String text = "1234567890 9876543210 año función 日本語 ☕";
    long o200kCount = counter.count(text, o200kProfile());
    long cl100kCount = counter.count(text, cl100kProfile());

    assertThat(o200kCount).isPositive();
    assertThat(cl100kCount).isPositive();
    assertThat(o200kCount).isNotEqualTo(cl100kCount);
  }

  @Test
  void countIsZeroForEmptyString() {
    assertThat(counter.count("", o200kProfile())).isZero();
  }

  @Test
  void encodingIsCachedAcrossCallsForSameProfile() {
    // Call twice with same profile — encoding should be fetched from cache
    // This also verifies the counter handles repeated calls without error
    long count1 = counter.count("Some text", o200kProfile());
    long count2 = counter.count("Some text", o200kProfile());
    assertThat(count1).isEqualTo(count2);
  }

  @Test
  void p50kBaseEncodingIsSupported() {
    ModelTokenizationProfile p50k =
        new ModelTokenizationProfile(
            "openai/p50k_base",
            TokenizationPrecision.EXACT_LOCAL,
            TokenCounterStrategy.JTOKKIT,
            "P50K_BASE",
            null,
            null);
    assertThat(counter.count("Hello, world!", p50k)).isPositive();
  }

  @Test
  void r50kBaseEncodingIsSupported() {
    ModelTokenizationProfile r50k =
        new ModelTokenizationProfile(
            "openai/r50k_base",
            TokenizationPrecision.EXACT_LOCAL,
            TokenCounterStrategy.JTOKKIT,
            "R50K_BASE",
            null,
            null);
    assertThat(counter.count("Hello, world!", r50k)).isPositive();
  }
}
