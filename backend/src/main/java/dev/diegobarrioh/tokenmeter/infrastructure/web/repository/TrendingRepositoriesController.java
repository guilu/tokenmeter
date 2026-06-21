package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import dev.diegobarrioh.tokenmeter.application.repository.TrendingQuery;
import dev.diegobarrioh.tokenmeter.application.repository.TrendingSuggestionsService;
import dev.diegobarrioh.tokenmeter.application.repository.TrendingSuggestionsService.TrendingSuggestions;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves "Popular this week" repository suggestions. Backed by {@link TrendingRepositoriesService}
 * (cached). Upstream GitHub failures surface as {@code 503} via {@link
 * RepositoryIntakeExceptionHandler}, never as a generic 500. The 15-minute {@code Cache-Control}
 * lets the browser/CDN absorb repeated reads on top of the backend cache.
 */
@RestController
public class TrendingRepositoriesController {

  private static final Duration CACHE_MAX_AGE = Duration.ofSeconds(900);

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
        .cacheControl(CacheControl.maxAge(CACHE_MAX_AGE).cachePublic())
        .body(
            TrendingRepositoriesResponse.from(
                suggestions.result(), suggestions.analyzedByRepositoryUrl()));
  }
}
