package dev.diegobarrioh.tokenmeter.infrastructure.github;

import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration block bound to {@code tokenmeter.github.*}. Token is wrapped in an {@link Optional}
 * so callers can distinguish "not set" from "set to blank". The {@link #toString()} override
 * redacts the token value to prevent leaking secrets into logs or stack traces.
 */
@ConfigurationProperties("tokenmeter.github")
public record GitHubProperties(
    String searchUrl,
    Optional<String> token,
    Duration connectTimeout,
    Duration readTimeout,
    String userAgent,
    Trending trending) {

  public GitHubProperties {
    if (searchUrl == null || searchUrl.isBlank()) {
      searchUrl = "https://api.github.com/search/repositories";
    }
    if (token == null) {
      token = Optional.empty();
    }
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(5);
    }
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(10);
    }
    if (userAgent == null || userAgent.isBlank()) {
      userAgent = "tokenmeter-backend/1.0";
    }
    if (trending == null) {
      trending = Trending.defaults();
    }
  }

  /**
   * Returns whether a non-blank token is configured. Convenience method for the adapter to decide
   * whether to send an Authorization header.
   */
  public boolean hasToken() {
    return token.filter(t -> !t.isBlank()).isPresent();
  }

  /**
   * Returns a string representation that redacts the token value. This prevents the secret from
   * appearing in logs, actuator endpoints, or exception messages.
   */
  @Override
  public String toString() {
    return "GitHubProperties["
        + "searchUrl="
        + searchUrl
        + ", token="
        + (token.isPresent() ? "***" : "<absent>")
        + ", connectTimeout="
        + connectTimeout
        + ", readTimeout="
        + readTimeout
        + ", userAgent="
        + userAgent
        + ", trending="
        + trending
        + ']';
  }

  /** Trending-specific sub-configuration. */
  public record Trending(Duration cacheTtl) {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(30);

    public Trending {
      if (cacheTtl == null) {
        cacheTtl = DEFAULT_CACHE_TTL;
      }
    }

    static Trending defaults() {
      return new Trending(DEFAULT_CACHE_TTL);
    }
  }
}
