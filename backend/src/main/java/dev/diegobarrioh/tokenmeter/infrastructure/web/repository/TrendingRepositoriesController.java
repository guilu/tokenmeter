package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import dev.diegobarrioh.tokenmeter.application.repository.TrendingQuery;
import dev.diegobarrioh.tokenmeter.application.repository.TrendingSuggestionsService;
import dev.diegobarrioh.tokenmeter.application.repository.TrendingSuggestionsService.TrendingSuggestions;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves "Popular this week" repository suggestions. Backed by {@link TrendingRepositoriesService}
 * (cached). Upstream GitHub failures surface as {@code 503} via {@link
 * RepositoryIntakeExceptionHandler}, never as a generic 500.
 *
 * <p>The response is sent {@code Cache-Control: no-store} (TKM-63): each item carries a dynamic
 * {@code analyzed} flag, so a freshly analyzed repository must show up the moment the user returns
 * to the home — an HTTP cache would serve a stale {@code analyzed:false}. The expensive GitHub call
 * is still absorbed by the in-memory cache inside {@link TrendingRepositoriesService}; only the
 * cheap analyzed batch query runs per request.
 */
@RestController
public class TrendingRepositoriesController {

  private final TrendingSuggestionsService service;

  public TrendingRepositoriesController(TrendingSuggestionsService service) {
    this.service = service;
  }

  @GetMapping("/api/repositories/trending")
  public ResponseEntity<TrendingRepositoriesResponse> getTrending(
      @RequestParam(defaultValue = "weekly") String since,
      @RequestParam(defaultValue = "12") int limit,
      @RequestParam(required = false) String language) {
    TrendingSuggestions suggestions = service.get(TrendingQuery.fromParams(since, limit, language));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            TrendingRepositoriesResponse.from(
                suggestions.result(), suggestions.analyzedByRepositoryUrl()));
  }
}
