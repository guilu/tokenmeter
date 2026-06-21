package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Wire shape for a single trending repository. Optional fields ({@code description}, {@code
 * language}, {@code sizeKb}) are omitted from JSON when null via {@link
 * JsonInclude.Include#NON_NULL}.
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
    boolean analyzed) {}
