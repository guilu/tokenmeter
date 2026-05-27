package dev.diegobarrioh.tokenmeter.domain.repository;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record wrapping a list of trending repositories fetched from the GitHub Search
 * API. {@code language} is null when no language filter was applied.
 */
public record TrendingRepositoriesResult(
    List<TrendingRepository> items, Instant fetchedAt, String since, String language) {}
