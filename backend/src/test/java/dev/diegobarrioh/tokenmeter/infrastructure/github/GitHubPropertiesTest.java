package dev.diegobarrioh.tokenmeter.infrastructure.github;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = GitHubPropertiesTest.Config.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "tokenmeter.github.search-url=https://api.github.com/search/repositories",
      "tokenmeter.github.token=test-secret-token",
      "tokenmeter.github.connect-timeout=5s",
      "tokenmeter.github.read-timeout=10s",
      "tokenmeter.github.user-agent=tokenmeter-test/1.0",
      "tokenmeter.github.trending.cache-ttl=PT30M"
    })
class GitHubPropertiesTest {

  @EnableConfigurationProperties(GitHubProperties.class)
  static class Config {}

  @Autowired GitHubProperties properties;

  @Test
  void searchUrlIsBinding() {
    assertThat(properties.searchUrl()).isEqualTo("https://api.github.com/search/repositories");
  }

  @Test
  void tokenIsPresent() {
    assertThat(properties.token()).isPresent();
    assertThat(properties.token().get()).isEqualTo("test-secret-token");
  }

  @Test
  void connectTimeoutIsBinding() {
    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void readTimeoutIsBinding() {
    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  void userAgentIsBinding() {
    assertThat(properties.userAgent()).isEqualTo("tokenmeter-test/1.0");
  }

  @Test
  void cacheTtlIsBinding() {
    assertThat(properties.trending().cacheTtl()).isEqualTo(Duration.ofMinutes(30));
  }
}
