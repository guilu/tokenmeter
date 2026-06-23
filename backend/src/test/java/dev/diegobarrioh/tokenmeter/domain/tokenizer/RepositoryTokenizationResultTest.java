package dev.diegobarrioh.tokenmeter.domain.tokenizer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies the semantic invariant introduced in Slice B: {@code totalTokens()} MUST equal {@code
 * tokensByTokenizerId.get("openai/o200k_base")}.
 *
 * <p>This invariant protects leaderboard scores, badge values, and markdown export from semantic
 * drift when multi-tokenizer counts are introduced.
 */
class RepositoryTokenizationResultTest {

  private static final String PRIMARY = "openai/o200k_base";
  private static final String SECONDARY = "anthropic/cl100k_heuristic";

  @Test
  void totalTokensEqualsO200kEntryInMap() {
    var result =
        new RepositoryTokenizationResult(
            "o200k_base", 2, 1000L, Map.of(PRIMARY, 1000L, SECONDARY, 950L), List.of(), Map.of());

    assertThat(result.totalTokens()).isEqualTo(1000L);
    assertThat(result.tokensByTokenizerId().get(PRIMARY)).isEqualTo(1000L);
    assertThat(result.totalTokens()).isEqualTo(result.tokensByTokenizerId().get(PRIMARY));
  }

  @Test
  void encodingFieldRemainsO200kBaseForMultiTokenizerResult() {
    var result =
        new RepositoryTokenizationResult(
            "o200k_base", 1, 500L, Map.of(PRIMARY, 500L, SECONDARY, 475L), List.of(), Map.of());

    assertThat(result.encoding()).isEqualTo("o200k_base");
  }

  @Test
  void heuristicCountSeparateFromPrimary() {
    var result =
        new RepositoryTokenizationResult(
            "o200k_base", 1, 1000L, Map.of(PRIMARY, 1000L, SECONDARY, 950L), List.of(), Map.of());

    assertThat(result.tokensByTokenizerId().get(SECONDARY)).isEqualTo(950L);
    assertThat(result.tokensByTokenizerId().get(PRIMARY)).isNotEqualTo(950L);
  }

  @Test
  void singleTokenizerMapStillSatisfiesInvariant() {
    var result =
        new RepositoryTokenizationResult(
            "o200k_base", 3, 750L, Map.of(PRIMARY, 750L), List.of(), Map.of());

    assertThat(result.totalTokens()).isEqualTo(result.tokensByTokenizerId().get(PRIMARY));
  }
}
