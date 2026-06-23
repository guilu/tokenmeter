package dev.diegobarrioh.tokenmeter.application.tokenizer;

import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenCounterStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * {@link TokenCounter} implementation that estimates token counts by scaling an o200k_base
 * reference count by a provider-specific factor.
 *
 * <p>The reference count is obtained from the injected {@link OpenAiTokenCounter}, which always
 * uses {@code O200K_BASE}. The heuristic factor comes from the profile's {@link
 * ModelTokenizationProfile#heuristicFactor()}. Rounding uses {@link RoundingMode#HALF_UP} to stay
 * deterministic across JVM versions.
 */
@Component
public class HeuristicTokenCounter implements TokenCounter {

  private final OpenAiTokenCounter referenceCounter;

  public HeuristicTokenCounter(OpenAiTokenCounter referenceCounter) {
    this.referenceCounter = referenceCounter;
  }

  @Override
  public boolean supports(ModelTokenizationProfile profile) {
    return profile.strategy() == TokenCounterStrategy.HEURISTIC;
  }

  @Override
  public long count(String text, ModelTokenizationProfile profile) {
    long reference = referenceCounter.count(text);
    if (reference == 0L) {
      return 0L;
    }
    return BigDecimal.valueOf(reference)
        .multiply(profile.heuristicFactor())
        .setScale(0, RoundingMode.HALF_UP)
        .longValue();
  }
}
