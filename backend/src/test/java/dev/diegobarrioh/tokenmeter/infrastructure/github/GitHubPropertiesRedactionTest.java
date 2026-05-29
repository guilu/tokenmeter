package dev.diegobarrioh.tokenmeter.infrastructure.github;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GitHubPropertiesRedactionTest {

  @Test
  void toStringDoesNotContainTokenValue() {
    GitHubProperties properties =
        new GitHubProperties(
            "https://api.github.com/search/repositories",
            Optional.of("super-secret-token"),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            "tokenmeter/1.0",
            new GitHubProperties.Trending(Duration.ofMinutes(30)));

    String str = properties.toString();

    assertThat(str).doesNotContain("super-secret-token");
  }

  @Test
  void toStringWithNoTokenDoesNotThrow() {
    GitHubProperties properties =
        new GitHubProperties(
            "https://api.github.com/search/repositories",
            Optional.empty(),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            "tokenmeter/1.0",
            new GitHubProperties.Trending(Duration.ofMinutes(30)));

    assertThat(properties.toString()).isNotBlank();
  }
}
