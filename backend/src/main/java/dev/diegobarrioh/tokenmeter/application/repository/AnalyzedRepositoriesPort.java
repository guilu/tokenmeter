package dev.diegobarrioh.tokenmeter.application.repository;

import java.util.Collection;
import java.util.Set;

/**
 * Outbound port that resolves, in a single batch query, which of the supplied normalized repository
 * URLs already have at least one successful analysis. Used to flag "Popular this week" suggestions
 * that TokenMeter has already analyzed (TKM-63) without issuing one query per card.
 */
public interface AnalyzedRepositoriesPort {

  /**
   * Returns the subset of {@code normalizedUrls} that have a stored successful analysis. Input URLs
   * are expected to be canonical ({@code https://github.com/owner/name}, lower-cased). An empty or
   * {@code null} input yields an empty set without hitting the database.
   */
  Set<String> analyzedRepositoryUrls(Collection<String> normalizedUrls);
}
