package dev.diegobarrioh.tokenmeter.application.tokenizer;

import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Selects the appropriate {@link TokenCounter} for a given {@link ModelTokenizationProfile} from
 * the list of registered counter beans.
 *
 * <p>Spring injects all {@link TokenCounter} beans via the {@code List<TokenCounter>} constructor
 * parameter. The first counter that {@link TokenCounter#supports(ModelTokenizationProfile)
 * supports} the profile is returned; if none do, an {@link IllegalStateException} is thrown. This
 * should never happen in production because the {@code HEURISTIC} strategy is the universal
 * fallback guaranteed by the DEFAULT profile in {@code tokenizer-profiles.yaml}.
 */
@Component
public class TokenCounterRegistry {

  private final List<TokenCounter> counters;

  public TokenCounterRegistry(List<TokenCounter> counters) {
    this.counters = List.copyOf(counters);
  }

  /**
   * Returns the first {@link TokenCounter} that supports the given profile.
   *
   * @param profile the profile to resolve a counter for
   * @return the matching counter
   * @throws IllegalStateException if no registered counter supports the profile
   */
  public TokenCounter resolve(ModelTokenizationProfile profile) {
    return counters.stream()
        .filter(counter -> counter.supports(profile))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No TokenCounter registered for profile: " + profile.tokenizerId()));
  }
}
