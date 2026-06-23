package dev.diegobarrioh.tokenmeter.domain.tokenizer;

import java.nio.file.Path;
import java.util.Map;

/**
 * Per-file token metrics. Carries a map of counts keyed by tokenizer ID so that multi-provider cost
 * estimation can use each model's dedicated tokenizer count.
 *
 * <p>{@link #tokens()} is a convenience that returns the primary OpenAI o200k count; all existing
 * callers that relied on the old {@code long tokens} field continue to compile and produce the same
 * value.
 */
public record FileTokenMetrics(
    Path relativePath, String language, Map<String, Long> tokensByTokenizerId) {

  /** Primary tokenizer identifier — OpenAI o200k_base, used as the display/leaderboard count. */
  public static final String PRIMARY_TOKENIZER_ID = "openai/o200k_base";

  /**
   * Returns the token count for the primary tokenizer ({@value #PRIMARY_TOKENIZER_ID}).
   *
   * <p>This accessor replaces the old {@code long tokens} field. It is semantically equivalent for
   * all callers that previously used {@code file.tokens()}.
   *
   * @return primary token count, or 0 if the primary tokenizer entry is absent
   */
  public long tokens() {
    Long count = tokensByTokenizerId.get(PRIMARY_TOKENIZER_ID);
    return count != null ? count : 0L;
  }
}
