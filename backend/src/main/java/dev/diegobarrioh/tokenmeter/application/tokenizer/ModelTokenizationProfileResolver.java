package dev.diegobarrioh.tokenmeter.application.tokenizer;

import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Application service that resolves a {@link ModelTokenizationProfile} for a given {@code
 * (AiProvider, modelId)} pair and computes the distinct set of tokenizer profiles required by a
 * pricing snapshot list.
 *
 * <p>Resolution is always safe: any exception from the catalog is caught and the DEFAULT profile is
 * returned. This guarantees that an unrecognised model never breaks the pipeline.
 */
@Service
public class ModelTokenizationProfileResolver {

  private final TokenizationProfileCatalog catalog;

  public ModelTokenizationProfileResolver(TokenizationProfileCatalog catalog) {
    this.catalog = catalog;
  }

  /**
   * Resolves the tokenization profile for the given provider and model.
   *
   * <p>Never throws; returns the DEFAULT profile on any error or miss.
   *
   * @param provider the AI provider, may be {@code null}
   * @param modelId the model identifier, may be {@code null}
   * @return a non-null {@link ModelTokenizationProfile}
   */
  public ModelTokenizationProfile resolve(AiProvider provider, String modelId) {
    try {
      return catalog.resolve(provider, modelId);
    } catch (Exception e) {
      return catalog.resolve(null, null);
    }
  }

  /**
   * Builds a deduplicated map of {@code tokenizerId → ModelTokenizationProfile} for all snapshots
   * in the list. This map is passed to {@code RepositoryTokenizationService.tokenize()} so the
   * service knows exactly which tokenizers to run.
   *
   * <p>Deduplication is by {@code tokenizerId}: if two different models share the same tokenizer
   * (e.g. {@code gpt-4o} and {@code gpt-4o-mini} both map to {@code openai/o200k_base}), only one
   * entry is kept.
   *
   * @param snapshots the active pricing snapshots; may be empty
   * @return an immutable map from tokenizerId to profile; never null
   */
  public Map<String, ModelTokenizationProfile> distinctTokenizers(List<PricingSnapshot> snapshots) {
    if (snapshots == null || snapshots.isEmpty()) {
      return Map.of();
    }
    LinkedHashMap<String, ModelTokenizationProfile> result = new LinkedHashMap<>();
    for (PricingSnapshot snapshot : snapshots) {
      ModelTokenizationProfile profile = resolve(snapshot.provider(), snapshot.model());
      result.putIfAbsent(profile.tokenizerId(), profile);
    }
    return Map.copyOf(result);
  }
}
