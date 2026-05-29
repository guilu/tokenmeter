package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepositoriesResult;
import java.time.Instant;
import java.util.List;

/**
 * Wire shape for {@code GET /api/repositories/trending}. {@code language} is omitted when no
 * language filter was applied.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrendingRepositoriesResponse(
    Instant fetchedAt, String since, String language, List<TrendingRepositoryResponse> items) {

  /** Maps a domain result to its wire representation. */
  public static TrendingRepositoriesResponse from(TrendingRepositoriesResult result) {
    List<TrendingRepositoryResponse> items =
        result.items().stream()
            .map(
                item ->
                    new TrendingRepositoryResponse(
                        item.fullName(),
                        item.repositoryUrl(),
                        item.description(),
                        item.language(),
                        item.stars(),
                        item.forks(),
                        item.sizeKb(),
                        item.createdAt(),
                        item.updatedAt()))
            .toList();
    return new TrendingRepositoriesResponse(
        result.fetchedAt(), result.since(), result.language(), items);
  }
}
