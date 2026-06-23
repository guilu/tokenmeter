package dev.diegobarrioh.tokenmeter.application.tokenizer;

import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;

/**
 * Application port for resolving a {@link ModelTokenizationProfile} from a provider+model pair.
 *
 * <p>Implementations (in {@code infrastructure/tokenizer/}) load the profile catalog at startup
 * from {@code tokenizer-profiles.yaml}. Resolution MUST never throw; unknown inputs MUST return the
 * DEFAULT profile.
 */
public interface TokenizationProfileCatalog {

  /**
   * Resolves the tokenization profile for the given provider and model identifier.
   *
   * @param provider the AI provider, or {@code null} for the DEFAULT fallback
   * @param modelId the model identifier, or {@code null} for the DEFAULT fallback
   * @return a non-null profile; the DEFAULT profile when no match is found
   */
  ModelTokenizationProfile resolve(AiProvider provider, String modelId);
}
