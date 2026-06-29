package dev.diegobarrioh.tokenmeter.domain.repository;

import java.time.Instant;

/**
 * Immutable domain record representing a single trending GitHub repository. Fields {@code
 * description}, {@code language}, {@code sizeKb}, {@code createdAt}, {@code updatedAt} and {@code
 * starsThisPeriod} are nullable because not all sources provide them.
 *
 * <p>{@code starsThisPeriod} represents stars gained in the trending window (day/week/month). It is
 * populated by the scrape adapter and {@code null} when the search-API fallback is used.
 */
public record TrendingRepository(
    String fullName,
    String repositoryUrl,
    String description,
    String language,
    int stars,
    int forks,
    Integer sizeKb,
    Instant createdAt,
    Instant updatedAt,
    Integer starsThisPeriod) {}
