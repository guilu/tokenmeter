package dev.diegobarrioh.tokenmeter.infrastructure.github;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    classes = {GitHubConfig.class, GitHubConfigTest.Extras.class},
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "tokenmeter.github.search-url=https://api.github.com/search/repositories",
      "tokenmeter.github.token=",
      "tokenmeter.github.connect-timeout=5s",
      "tokenmeter.github.read-timeout=10s",
      "tokenmeter.github.user-agent=tokenmeter-test/1.0",
      "tokenmeter.github.trending.cache-ttl=PT30M"
    })
class GitHubConfigTest {

  @org.springframework.context.annotation.Configuration
  static class Extras {
    @org.springframework.context.annotation.Bean
    RestClient.Builder restClientBuilder() {
      return RestClient.builder();
    }
  }

  @Autowired
  @Qualifier("gitHubSearchClient")
  RestClient gitHubSearchClient;

  @Test
  void gitHubSearchClientBeanIsCreated() {
    assertThat(gitHubSearchClient).isNotNull();
  }
}
