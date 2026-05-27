package dev.diegobarrioh.tokenmeter.infrastructure.github;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Registers {@link GitHubProperties} and the project-scoped {@link RestClient} used to call the
 * GitHub Search API. The bean is named {@code gitHubSearchClient} to avoid collision with other
 * {@code RestClient} beans (e.g. {@code pricingLiteLlmClient}).
 */
@Configuration
@EnableConfigurationProperties(GitHubProperties.class)
public class GitHubConfig {

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
        .defaultHeader("User-Agent", properties.userAgent())
        .defaultHeader("Accept", "application/vnd.github+json")
        .build();
  }
}
