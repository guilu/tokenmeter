package dev.diegobarrioh.tokenmeter.application.repository;

import dev.diegobarrioh.tokenmeter.domain.repository.GitHubRepositoryUrl;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepositoriesResult;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepository;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Composes the cached trending result with the "already analyzed" flag for each suggestion
 * (TKM-63). The analyzed lookup is resolved in a single batch query and is intentionally NOT cached
 * alongside the trending payload, so a freshly analyzed repository is reflected on the next
 * request.
 */
@Service
public class TrendingSuggestionsService {

  private final TrendingRepositoriesService trending;
  private final AnalyzedRepositoriesPort analyzedRepositories;

  public TrendingSuggestionsService(
      TrendingRepositoriesService trending, AnalyzedRepositoriesPort analyzedRepositories) {
    this.trending = trending;
    this.analyzedRepositories = analyzedRepositories;
  }

  public TrendingSuggestions get(TrendingQuery query) {
    TrendingRepositoriesResult result = trending.get(query);

    Map<String, String> normalizedByRawUrl = new LinkedHashMap<>();
    for (TrendingRepository item : result.items()) {
      String normalized = normalize(item.repositoryUrl());
      if (normalized != null) {
        normalizedByRawUrl.put(item.repositoryUrl(), normalized);
      }
    }

    Set<String> analyzedNormalized =
        analyzedRepositories.analyzedRepositoryUrls(new HashSet<>(normalizedByRawUrl.values()));

    Map<String, Boolean> analyzedByRawUrl = new LinkedHashMap<>();
    for (TrendingRepository item : result.items()) {
      String normalized = normalizedByRawUrl.get(item.repositoryUrl());
      analyzedByRawUrl.put(
          item.repositoryUrl(), normalized != null && analyzedNormalized.contains(normalized));
    }

    return new TrendingSuggestions(result, analyzedByRawUrl);
  }

  private static String normalize(String repositoryUrl) {
    try {
      return GitHubRepositoryUrl.parse(repositoryUrl).normalizedUrl();
    } catch (RepositoryIntakeException ex) {
      return null;
    }
  }

  /**
   * Trending result plus, keyed by each item's raw {@code repositoryUrl}, whether TokenMeter has
   * already analyzed it.
   */
  public record TrendingSuggestions(
      TrendingRepositoriesResult result, Map<String, Boolean> analyzedByRepositoryUrl) {}
}
