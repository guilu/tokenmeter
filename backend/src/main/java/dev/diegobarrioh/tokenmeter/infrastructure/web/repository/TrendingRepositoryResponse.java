package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Wire shape for a single trending repository. Optional fields ({@code description}, {@code
 * language}, {@code sizeKb}, {@code createdAt}, {@code updatedAt}, {@code starsThisPeriod}) are
 * omitted from JSON when null via {@link JsonInclude.Include#NON_NULL}.
 *
 * <p>{@code starsThisPeriod} is populated by the scrape adapter (stars gained in the selected
 * trending window) and absent when the Search API fallback was used.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrendingRepositoryResponse(
    String fullName,
    String repositoryUrl,
    String description,
    String language,
    int stars,
    int forks,
    Integer sizeKb,
    Instant createdAt,
    Instant updatedAt,
    boolean analyzed,
    Integer starsThisPeriod) {}
