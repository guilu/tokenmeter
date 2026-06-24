package dev.diegobarrioh.tokenmeter.domain.tokenizer;

import java.math.BigDecimal;

/**
 * Immutable value object that describes how to tokenize text for a specific model.
 *
 * <p>Constraints enforced by the compact constructor:
 *
 * <ul>
 *   <li>{@code tokenizerId} must be non-null and non-blank.
 *   <li>{@code precision} and {@code strategy} must be non-null.
 *   <li>If {@code strategy == JTOKKIT} then {@code encoding} must be non-null and non-blank.
 *   <li>If {@code strategy == HEURISTIC} then {@code heuristicFactor} must be non-null and
 *       positive.
 *   <li>If {@code strategy == HF_LOCAL} then {@code hfModelPath} must be non-null and non-blank.
 * </ul>
 *
 * @param tokenizerId logical identifier keying this profile (e.g. {@code "openai/o200k_base"})
 * @param precision how accurate the resulting count is
 * @param strategy which counter implementation to use
 * @param encoding jtokkit {@code EncodingType} name (non-null only for {@code JTOKKIT} strategy)
 * @param heuristicFactor multiplicative factor over the o200k reference count (positive, non-null
 *     only for {@code HEURISTIC} strategy)
 * @param hfModelPath classpath-relative path to the vendored {@code tokenizer.json} file under
 *     {@code tokenizers/} (non-null and non-blank only for {@code HF_LOCAL} strategy, e.g. {@code
 *     "deepseek/tokenizer.json"})
 */
public record ModelTokenizationProfile(
    String tokenizerId,
    TokenizationPrecision precision,
    TokenCounterStrategy strategy,
    String encoding,
    BigDecimal heuristicFactor,
    String hfModelPath) {

  public ModelTokenizationProfile {
    if (tokenizerId == null || tokenizerId.isBlank()) {
      throw new IllegalArgumentException("tokenizerId is required");
    }
    if (precision == null) {
      throw new IllegalArgumentException("precision is required");
    }
    if (strategy == null) {
      throw new IllegalArgumentException("strategy is required");
    }
    if (strategy == TokenCounterStrategy.JTOKKIT && (encoding == null || encoding.isBlank())) {
      throw new IllegalArgumentException("encoding is required for JTOKKIT strategy");
    }
    if (strategy == TokenCounterStrategy.HEURISTIC
        && (heuristicFactor == null || heuristicFactor.signum() <= 0)) {
      throw new IllegalArgumentException(
          "heuristicFactor must be non-null and positive for HEURISTIC strategy");
    }
    if (strategy == TokenCounterStrategy.HF_LOCAL
        && (hfModelPath == null || hfModelPath.isBlank())) {
      throw new IllegalArgumentException("hfModelPath is required for HF_LOCAL strategy");
    }
  }
}
