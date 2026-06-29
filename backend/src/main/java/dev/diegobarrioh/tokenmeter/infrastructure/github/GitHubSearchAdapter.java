package dev.diegobarrioh.tokenmeter.infrastructure.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.diegobarrioh.tokenmeter.application.repository.TrendingQuery;
import dev.diegobarrioh.tokenmeter.application.repository.TrendingRepositoriesPort;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepositoriesResult;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * GitHub Search API adapter implementing {@link TrendingRepositoriesPort}. Uses the project-scoped
 * {@code gitHubSearchClient} bean (configured in {@link GitHubConfig}).
 *
 * <p>Error translation:
 *
 * <ul>
 *   <li>HTTP 403 + {@code x-ratelimit-remaining: 0} → {@code GITHUB_RATE_LIMITED} → 503
 *   <li>HTTP 5xx, IO / timeout → {@code GITHUB_UNAVAILABLE} → 503
 * </ul>
 *
 * <p>Token handling: Authorization header added per-request only when a non-blank token is
 * configured. The token is NEVER included in log output.
 */
@Component
public class GitHubSearchAdapter implements TrendingRepositoriesPort {

  private static final Logger LOG = LoggerFactory.getLogger(GitHubSearchAdapter.class);

  private final RestClient restClient;
  private final GitHubProperties properties;
  private final Clock clock;

  public GitHubSearchAdapter(
      @Qualifier("gitHubSearchClient") RestClient restClient,
      GitHubProperties properties,
      Clock clock) {
    this.restClient = restClient;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public TrendingRepositoriesResult fetch(TrendingQuery query) {
    String q = GitHubQueryBuilder.build(query, clock);
    String url =
        UriComponentsBuilder.fromUriString(properties.searchUrl())
            .queryParam("q", q)
            .queryParam("sort", "stars")
            .queryParam("order", "desc")
            .queryParam("per_page", query.limit())
            .build()
            .toUriString();

    LOG.debug("Fetching trending repositories: since={}, limit={}", query.since(), query.limit());

    try {
      var spec = restClient.get().uri(url);

      // Add Authorization header only when token is non-blank; never log the value.
      if (properties.hasToken()) {
        String bearerValue = "Bearer " + properties.token().get();
        spec = spec.header("Authorization", bearerValue);
      }

      GitHubSearchResponse response =
          spec.retrieve()
              .onStatus(
                  HttpStatusCode::is4xxClientError,
                  (req, resp) -> {
                    if (resp.getStatusCode().value() == 403) {
                      String remaining = resp.getHeaders().getFirst("x-ratelimit-remaining");
                      if ("0".equals(remaining)) {
                        throw new RepositoryIntakeException(
                            RepositoryIntakeErrorCode.GITHUB_RATE_LIMITED,
                            "GitHub rate limit exceeded");
                      }
                    }
                    throw new RepositoryIntakeException(
                        RepositoryIntakeErrorCode.GITHUB_UNAVAILABLE,
                        "GitHub Search API returned " + resp.getStatusCode());
                  })
              .onStatus(
                  HttpStatusCode::is5xxServerError,
                  (req, resp) -> {
                    throw new RepositoryIntakeException(
                        RepositoryIntakeErrorCode.GITHUB_UNAVAILABLE,
                        "GitHub Search API unavailable: " + resp.getStatusCode());
                  })
              .body(GitHubSearchResponse.class);

      if (response == null || response.items() == null) {
        return new TrendingRepositoriesResult(
            List.of(), Instant.now(clock), query.since().name().toLowerCase(), null);
      }

      List<TrendingRepository> items =
          response.items().stream().map(GitHubSearchAdapter::toRepository).toList();
      LOG.debug("Fetched {} trending repositories", Integer.valueOf(items.size()));

      return new TrendingRepositoriesResult(
          items,
          Instant.now(clock),
          query.since().name().toLowerCase(),
          query.language().orElse(null));

    } catch (RepositoryIntakeException e) {
      throw e;
    } catch (ResourceAccessException e) {
      LOG.warn("GitHub Search API IO error: {}", e.getMessage());
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.GITHUB_UNAVAILABLE, "GitHub Search API unavailable", e);
    } catch (RestClientResponseException e) {
      LOG.warn("GitHub Search API error: status={}", Integer.valueOf(e.getStatusCode().value()));
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.GITHUB_UNAVAILABLE, "GitHub Search API unavailable", e);
    } catch (RestClientException e) {
      LOG.warn("GitHub Search API request failed: {}", e.getMessage());
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.GITHUB_UNAVAILABLE, "GitHub Search API unavailable", e);
    }
  }

  private static TrendingRepository toRepository(GitHubItem item) {
    return new TrendingRepository(
        item.fullName(),
        item.htmlUrl(),
        item.description(),
        item.language(),
        item.stargazersCount(),
        item.forksCount(),
        item.size(),
        item.createdAt(),
        item.updatedAt(),
        null);
  }

  // --- Internal DTOs (package-private for testing via Jackson) ---

  @JsonIgnoreProperties(ignoreUnknown = true)
  record GitHubSearchResponse(
      @JsonProperty("total_count") int totalCount, @JsonProperty("items") List<GitHubItem> items) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record GitHubItem(
      @JsonProperty("full_name") String fullName,
      @JsonProperty("html_url") String htmlUrl,
      @JsonProperty("description") String description,
      @JsonProperty("language") String language,
      @JsonProperty("stargazers_count") int stargazersCount,
      @JsonProperty("forks_count") int forksCount,
      @JsonProperty("size") Integer size,
      @JsonProperty("created_at") Instant createdAt,
      @JsonProperty("updated_at") Instant updatedAt) {}
}
