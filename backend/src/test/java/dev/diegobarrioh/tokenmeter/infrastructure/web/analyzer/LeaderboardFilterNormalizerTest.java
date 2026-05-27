package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LeaderboardFilterNormalizerTest {

  @Test
  void normalizeModeReturnsNullForNull() {
    assertThat(LeaderboardFilterNormalizer.normalizeMode(null)).isNull();
  }

  @Test
  void normalizeModeReturnsNullForBlank() {
    assertThat(LeaderboardFilterNormalizer.normalizeMode("   ")).isNull();
  }

  @Test
  void normalizeModeReturnsNullForInvalidValue() {
    assertThat(LeaderboardFilterNormalizer.normalizeMode("INVALID")).isNull();
    assertThat(LeaderboardFilterNormalizer.normalizeMode("notamode")).isNull();
  }

  @Test
  void normalizeModeReturnsUppercaseForValidValues() {
    assertThat(LeaderboardFilterNormalizer.normalizeMode("raw")).isEqualTo("RAW");
    assertThat(LeaderboardFilterNormalizer.normalizeMode("ASSISTED")).isEqualTo("ASSISTED");
    assertThat(LeaderboardFilterNormalizer.normalizeMode("agentic")).isEqualTo("AGENTIC");
  }

  @Test
  void normalizeModeIsCaseInsensitive() {
    assertThat(LeaderboardFilterNormalizer.normalizeMode("Raw")).isEqualTo("RAW");
    assertThat(LeaderboardFilterNormalizer.normalizeMode("AGENTIC")).isEqualTo("AGENTIC");
    assertThat(LeaderboardFilterNormalizer.normalizeMode("assisted")).isEqualTo("ASSISTED");
  }

  @Test
  void normalizeProviderReturnsNullForNull() {
    assertThat(LeaderboardFilterNormalizer.normalizeProvider(null)).isNull();
  }

  @Test
  void normalizeProviderReturnsNullForBlank() {
    assertThat(LeaderboardFilterNormalizer.normalizeProvider("  ")).isNull();
  }

  @Test
  void normalizeProviderReturnsNullForUnknownProvider() {
    assertThat(LeaderboardFilterNormalizer.normalizeProvider("UNKNOWN_PROVIDER")).isNull();
    assertThat(LeaderboardFilterNormalizer.normalizeProvider("fake")).isNull();
  }

  @Test
  void normalizeProviderReturnsUppercaseForValidProviders() {
    assertThat(LeaderboardFilterNormalizer.normalizeProvider("openai")).isEqualTo("OPENAI");
    assertThat(LeaderboardFilterNormalizer.normalizeProvider("ANTHROPIC")).isEqualTo("ANTHROPIC");
  }

  @Test
  void normalizeModelReturnsNullForNull() {
    assertThat(LeaderboardFilterNormalizer.normalizeModel(null)).isNull();
  }

  @Test
  void normalizeModelReturnsNullForBlank() {
    assertThat(LeaderboardFilterNormalizer.normalizeModel("   ")).isNull();
  }

  @Test
  void normalizeModelReturnsTrimmedValue() {
    assertThat(LeaderboardFilterNormalizer.normalizeModel("gpt-4o")).isEqualTo("gpt-4o");
    assertThat(LeaderboardFilterNormalizer.normalizeModel("  gpt-4o  ")).isEqualTo("gpt-4o");
  }
}
