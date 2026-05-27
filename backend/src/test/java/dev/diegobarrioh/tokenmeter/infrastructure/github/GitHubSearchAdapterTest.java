package dev.diegobarrioh.tokenmeter.infrastructure.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.diegobarrioh.tokenmeter.application.repository.TrendingQuery;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepositoriesResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubSearchAdapterTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-05-27T12:00:00Z"), ZoneOffset.UTC);

  private static final String SEARCH_URL = "https://api.github.com/search/repositories";

  private MockRestServiceServer mockServer;
  private GitHubSearchAdapter adapter;
  private RestClient.Builder builder;

  @BeforeEach
  void setUp() {
    builder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.baseUrl(SEARCH_URL).build();

    GitHubProperties properties =
        new GitHubProperties(
            SEARCH_URL,
            Optional.empty(),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            "tokenmeter-test/1.0",
            new GitHubProperties.Trending(Duration.ofMinutes(30)));

    adapter = new GitHubSearchAdapter(restClient, properties, FIXED_CLOCK);
  }

  @Test
  void happyPathMapsAllFields() {
    mockServer
        .expect(requestTo(Matchers.containsString("search/repositories")))
        .andRespond(withSuccess(fullItemJson(), MediaType.APPLICATION_JSON));

    TrendingQuery query = TrendingQuery.fromParams("weekly", 12, null);
    TrendingRepositoriesResult result = adapter.fetch(query);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).fullName()).isEqualTo("torvalds/linux");
    assertThat(result.items().get(0).repositoryUrl())
        .isEqualTo("https://github.com/torvalds/linux");
    assertThat(result.items().get(0).description()).isEqualTo("Linux kernel source tree");
    assertThat(result.items().get(0).language()).isEqualTo("C");
    assertThat(result.items().get(0).stars()).isEqualTo(176000);
    assertThat(result.items().get(0).forks()).isEqualTo(10500);
    assertThat(result.items().get(0).sizeKb()).isEqualTo(1073741);
    assertThat(result.items().get(0).createdAt()).isNotNull();
    assertThat(result.items().get(0).updatedAt()).isNotNull();
  }

  @Test
  void nullDescriptionAndLanguageMapToNull() {
    mockServer
        .expect(requestTo(Matchers.containsString("search/repositories")))
        .andRespond(withSuccess(nullFieldsItemJson(), MediaType.APPLICATION_JSON));

    TrendingQuery query = TrendingQuery.fromParams("weekly", 12, null);
    TrendingRepositoriesResult result = adapter.fetch(query);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).description()).isNull();
    assertThat(result.items().get(0).language()).isNull();
  }

  @Test
  void github403WithRateLimitHeaderThrowsGithubRateLimited() {
    mockServer
        .expect(requestTo(Matchers.containsString("search/repositories")))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN).header("x-ratelimit-remaining", "0").body("{}"));

    TrendingQuery query = TrendingQuery.fromParams("weekly", 12, null);

    assertThatThrownBy(() -> adapter.fetch(query))
        .isInstanceOf(RepositoryIntakeException.class)
        .satisfies(
            ex -> {
              RepositoryIntakeException rie = (RepositoryIntakeException) ex;
              assertThat(rie.errorCode()).isEqualTo(RepositoryIntakeErrorCode.GITHUB_RATE_LIMITED);
            });
  }

  @Test
  void github5xxThrowsGithubUnavailable() {
    mockServer
        .expect(requestTo(Matchers.containsString("search/repositories")))
        .andRespond(withServerError());

    TrendingQuery query = TrendingQuery.fromParams("weekly", 12, null);

    assertThatThrownBy(() -> adapter.fetch(query))
        .isInstanceOf(RepositoryIntakeException.class)
        .satisfies(
            ex -> {
              RepositoryIntakeException rie = (RepositoryIntakeException) ex;
              assertThat(rie.errorCode()).isEqualTo(RepositoryIntakeErrorCode.GITHUB_UNAVAILABLE);
            });
  }

  @Test
  void tokenPresentAddsAuthorizationHeader() {
    RestClient.Builder tokenBuilder = RestClient.builder();
    MockRestServiceServer tokenServer = MockRestServiceServer.bindTo(tokenBuilder).build();
    RestClient restClientWithToken = tokenBuilder.baseUrl(SEARCH_URL).build();

    GitHubProperties propertiesWithToken =
        new GitHubProperties(
            SEARCH_URL,
            Optional.of("my-secret-token"),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            "tokenmeter-test/1.0",
            new GitHubProperties.Trending(Duration.ofMinutes(30)));

    GitHubSearchAdapter adapterWithToken =
        new GitHubSearchAdapter(restClientWithToken, propertiesWithToken, FIXED_CLOCK);

    tokenServer
        .expect(requestTo(Matchers.containsString("search/repositories")))
        .andExpect(header("Authorization", "Bearer my-secret-token"))
        .andRespond(withSuccess(fullItemJson(), MediaType.APPLICATION_JSON));

    adapterWithToken.fetch(TrendingQuery.fromParams("weekly", 12, null));

    tokenServer.verify();
  }

  @Test
  void tokenAbsentNoAuthorizationHeader() {
    mockServer
        .expect(requestTo(Matchers.containsString("search/repositories")))
        .andExpect(
            request -> assertThat(request.getHeaders().containsKey("Authorization")).isFalse())
        .andRespond(withSuccess(fullItemJson(), MediaType.APPLICATION_JSON));

    adapter.fetch(TrendingQuery.fromParams("weekly", 12, null));

    mockServer.verify();
  }

  @Test
  void tokenNeverAppearsInLogs() {
    String secretToken = "ultra-secret-token-for-log-test";

    RestClient.Builder logBuilder = RestClient.builder();
    MockRestServiceServer logServer = MockRestServiceServer.bindTo(logBuilder).build();
    RestClient restClientLog = logBuilder.baseUrl(SEARCH_URL).build();

    GitHubProperties sensitiveProperties =
        new GitHubProperties(
            SEARCH_URL,
            Optional.of(secretToken),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            "tokenmeter-test/1.0",
            new GitHubProperties.Trending(Duration.ofMinutes(30)));

    GitHubSearchAdapter adapterWithSecret =
        new GitHubSearchAdapter(restClientLog, sensitiveProperties, FIXED_CLOCK);

    Logger adapterLogger = (Logger) LoggerFactory.getLogger(GitHubSearchAdapter.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    adapterLogger.addAppender(listAppender);
    adapterLogger.setLevel(Level.ALL);

    logServer
        .expect(requestTo(Matchers.containsString("search/repositories")))
        .andRespond(withSuccess(fullItemJson(), MediaType.APPLICATION_JSON));

    try {
      adapterWithSecret.fetch(TrendingQuery.fromParams("weekly", 12, null));
    } finally {
      adapterLogger.detachAppender(listAppender);
    }

    for (ILoggingEvent event : listAppender.list) {
      assertThat(event.getFormattedMessage()).doesNotContain(secretToken);
    }
  }

  // --- JSON helpers ---

  private static String fullItemJson() {
    return """
        {
          "total_count": 1,
          "items": [
            {
              "full_name": "torvalds/linux",
              "html_url": "https://github.com/torvalds/linux",
              "description": "Linux kernel source tree",
              "language": "C",
              "stargazers_count": 176000,
              "forks_count": 10500,
              "size": 1073741,
              "created_at": "1991-09-17T00:00:00Z",
              "updated_at": "2026-05-27T10:00:00Z"
            }
          ]
        }
        """;
  }

  private static String nullFieldsItemJson() {
    return """
        {
          "total_count": 1,
          "items": [
            {
              "full_name": "owner/no-desc",
              "html_url": "https://github.com/owner/no-desc",
              "description": null,
              "language": null,
              "stargazers_count": 100,
              "forks_count": 5,
              "size": 512,
              "created_at": "2020-01-01T00:00:00Z",
              "updated_at": "2026-05-01T00:00:00Z"
            }
          ]
        }
        """;
  }
}
