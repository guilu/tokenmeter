package dev.diegobarrioh.tokenmeter.domain.repository;

import java.time.Instant;

/**
 * Immutable domain record representing a single trending GitHub repository. Fields {@code
 * description}, {@code language} and {@code sizeKb} are nullable because GitHub may omit them.
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
    Instant updatedAt) {}
