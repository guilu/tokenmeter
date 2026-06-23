package dev.diegobarrioh.tokenmeter.application.tokenizer;

import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;

/**
 * SPI for counting tokens in a text string using a specific tokenization strategy.
 *
 * <p>Implementations are registered as Spring beans and collected by {@link TokenCounterRegistry}.
 * Each implementation is responsible for declaring which profiles it can handle via {@link
 * #supports(ModelTokenizationProfile)}.
 */
public interface TokenCounter {

  /**
   * Returns {@code true} if this counter can process the given profile.
   *
   * @param profile the profile to check
   * @return {@code true} if this counter supports the profile's strategy
   */
  boolean supports(ModelTokenizationProfile profile);

  /**
   * Counts the tokens in {@code text} according to the profile's strategy and encoding.
   *
   * @param text the text to tokenize; empty string must return 0
   * @param profile the profile describing the encoding or factor to apply
   * @return the token count (≥ 0)
   */
  long count(String text, ModelTokenizationProfile profile);
}
