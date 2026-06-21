package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepositoriesResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Wire shape for {@code GET /api/repositories/trending}. {@code language} is omitted when no
 * language filter was applied.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrendingRepositoriesResponse(
    Instant fetchedAt, String since, String language, List<TrendingRepositoryResponse> items) {

  /**
   * Maps a domain result to its wire representation, flagging each item as analyzed via {@code
   * analyzedByRepositoryUrl} keyed on the item's raw {@code repositoryUrl} (TKM-63).
   */
  public static TrendingRepositoriesResponse from(
      TrendingRepositoriesResult result, Map<String, Boolean> analyzedByRepositoryUrl) {
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
                        item.updatedAt(),
                        analyzedByRepositoryUrl.getOrDefault(item.repositoryUrl(), false)))
            .toList();
    return new TrendingRepositoriesResponse(
        result.fetchedAt(), result.since(), result.language(), items);
  }
}
