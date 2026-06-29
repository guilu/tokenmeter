package dev.diegobarrioh.tokenmeter.infrastructure.github;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Registers {@link GitHubProperties} and the project-scoped {@link RestClient} beans used to call
 * GitHub. Bean names avoid collision with other {@code RestClient} beans (e.g. {@code
 * pricingLiteLlmClient}).
 *
 * <ul>
 *   <li>{@code gitHubSearchClient} – GitHub Search API (JSON, authenticated when token present).
 *   <li>{@code gitHubTrendingClient} – GitHub Trending HTML page (browser-like User-Agent so GitHub
 *       does not block the scrape request).
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(GitHubProperties.class)
public class GitHubConfig {

  private static final String TRENDING_BASE_URL = "https://github.com";
  private static final String TRENDING_USER_AGENT = "Mozilla/5.0 (compatible; TokenMeter/1.0)";

  /**
   * Project-scoped {@link RestClient} for the GitHub Search API. Connect and read timeouts come
   * from {@link GitHubProperties}. The User-Agent header is set as a default so every request
   * complies with GitHub's requirement that API clients identify themselves.
   */
  @Bean
  public RestClient gitHubSearchClient(RestClient.Builder builder, GitHubProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
    requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());
    return builder
        .requestFactory(requestFactory)
        .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
        .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
        .build();
  }

  /**
   * Project-scoped {@link RestClient} for the GitHub Trending HTML page. Uses a browser-like
   * User-Agent and {@code Accept: text/html} so GitHub serves the full trending page rather than a
   * redirect or an API error.
   */
  @Bean
  public RestClient gitHubTrendingClient(RestClient.Builder builder, GitHubProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
    requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());
    return builder
        .requestFactory(requestFactory)
        .baseUrl(TRENDING_BASE_URL)
        .defaultHeader(HttpHeaders.USER_AGENT, TRENDING_USER_AGENT)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
        .build();
  }
}
