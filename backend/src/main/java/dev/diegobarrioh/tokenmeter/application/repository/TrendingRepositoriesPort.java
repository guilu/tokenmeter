package dev.diegobarrioh.tokenmeter.application.repository;

import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepositoriesResult;

/**
 * Outbound port for fetching trending repositories. The application service delegates cache misses
 * to this port; the GitHub adapter implements it.
 */
public interface TrendingRepositoriesPort {

  /**
   * Fetches trending repositories from the upstream provider.
   *
   * @param query normalised query parameters
   * @return a result containing the items and metadata
   * @throws dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException with {@code
   *     GITHUB_RATE_LIMITED} or {@code GITHUB_UNAVAILABLE} on upstream errors
   */
  TrendingRepositoriesResult fetch(TrendingQuery query);
}
