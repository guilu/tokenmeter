package dev.diegobarrioh.tokenmeter.domain.tokenizer;

import java.util.List;
import java.util.Map;

/**
 * Aggregated result of tokenizing a repository.
 *
 * <p>The {@link #tokensByTokenizerId()} map carries per-tokenizer totals for multi-provider cost
 * estimation. The primary token count ({@link #totalTokens()}) is always the sum of the o200k_base
 * counts so leaderboard scores, badge values, and markdown export remain unaffected.
 *
 * <p>Invariant: {@code totalTokens() == tokensByTokenizerId().get("openai/o200k_base")}.
 */
public record RepositoryTokenizationResult(
    String encoding,
    long totalFiles,
    long totalTokens,
    Map<String, Long> tokensByTokenizerId,
    List<FileTokenMetrics> files,
    Map<String, LanguageTokenMetrics> languages) {

  public RepositoryTokenizationResult {
    if (tokensByTokenizerId != null) {
      Long primary = tokensByTokenizerId.get(FileTokenMetrics.PRIMARY_TOKENIZER_ID);
      if (primary != null && primary.longValue() != totalTokens) {
        throw new IllegalArgumentException(
            "totalTokens (%d) must equal the primary tokenizer total (%d)"
                .formatted(Long.valueOf(totalTokens), primary));
      }
    }
  }
}
