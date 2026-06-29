package dev.diegobarrioh.tokenmeter.infrastructure.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.diegobarrioh.tokenmeter.application.repository.TrendingQuery;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepositoriesResult;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubTrendingScrapeAdapterTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-06-29T12:00:00Z"), ZoneOffset.UTC);

  private static final String TRENDING_BASE_URL = "https://github.com";

  private MockRestServiceServer mockServer;
  private GitHubTrendingScrapeAdapter adapter;
  private GitHubSearchAdapter fallback;
  private RestClient.Builder builder;

  @BeforeEach
  void setUp() {
    builder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(builder).build();
    RestClient trendingClient = builder.baseUrl(TRENDING_BASE_URL).build();

    fallback = mock(GitHubSearchAdapter.class);

    adapter = new GitHubTrendingScrapeAdapter(trendingClient, fallback, FIXED_CLOCK);
  }

  // --- Parser tests (static parse method, uses real HTML fixture) ---

  @Test
  void parsesRealFixtureAndExtractsExpectedValues() throws IOException {
    String html = loadFixture("github/trending-weekly.html");

    TrendingQuery query = TrendingQuery.fromParams("weekly", 25, null);
    List<TrendingRepository> items = GitHubTrendingScrapeAdapter.parse(html, query);

    // Fixture has 21 articles; limit=25 so we get all of them
    assertThat(items).hasSizeGreaterThanOrEqualTo(1);

    TrendingRepository first = items.get(0);
    assertThat(first.fullName()).isEqualTo("calesthio/OpenMontage");
    assertThat(first.repositoryUrl()).isEqualTo("https://github.com/calesthio/OpenMontage");
    assertThat(first.language()).isEqualTo("Python");
    assertThat(first.stars()).isGreaterThan(0);
    assertThat(first.forks()).isGreaterThan(0);
    assertThat(first.starsThisPeriod()).isNotNull().isGreaterThan(0);
    assertThat(first.description()).isNotBlank();
  }

  @Test
  void limitIsRespected() throws IOException {
    String html = loadFixture("github/trending-weekly.html");

    TrendingQuery query = TrendingQuery.fromParams("weekly", 3, null);
    List<TrendingRepository> items = GitHubTrendingScrapeAdapter.parse(html, query);

    assertThat(items).hasSize(3);
  }

  @Test
  void fullNameAndUrlAreConstructedFromHref() throws IOException {
    String html = loadFixture("github/trending-weekly.html");

    TrendingQuery query = TrendingQuery.fromParams("weekly", 1, null);
    TrendingRepository first = GitHubTrendingScrapeAdapter.parse(html, query).get(0);

    // fullName = owner/repo (no leading slash)
    assertThat(first.fullName()).doesNotStartWith("/");
    assertThat(first.fullName()).contains("/");
    // htmlUrl = https://github.com + href
    assertThat(first.repositoryUrl()).startsWith("https://github.com/");
  }

  @Test
  void starsThisPeriodParsedFromPeriodSpan() throws IOException {
    String html = loadFixture("github/trending-weekly.html");

    TrendingQuery query = TrendingQuery.fromParams("weekly", 1, null);
    TrendingRepository first = GitHubTrendingScrapeAdapter.parse(html, query).get(0);

    // The fixture is from a weekly trending page, so starsThisPeriod should be a positive number
    assertThat(first.starsThisPeriod()).isNotNull().isPositive();
  }

  @Test
  void sizeKbCreatedAtUpdatedAtAreNullForScrapedResults() throws IOException {
    String html = loadFixture("github/trending-weekly.html");

    TrendingQuery query = TrendingQuery.fromParams("weekly", 1, null);
    TrendingRepository first = GitHubTrendingScrapeAdapter.parse(html, query).get(0);

    // Trending page does not expose sizeKb, createdAt, or updatedAt
    assertThat(first.sizeKb()).isNull();
    assertThat(first.createdAt()).isNull();
    assertThat(first.updatedAt()).isNull();
  }

  // --- HTTP + fallback tests ---

  @Test
  void happyPathReturnsParsedItems() throws IOException {
    String html = loadFixture("github/trending-weekly.html");

    mockServer
        .expect(requestTo(Matchers.containsString("trending")))
        .andRespond(withSuccess(html, MediaType.TEXT_HTML));

    TrendingQuery query = TrendingQuery.fromParams("weekly", 5, null);
    TrendingRepositoriesResult result = adapter.fetch(query);

    assertThat(result.items()).hasSize(5);
    assertThat(result.since()).isEqualTo("weekly");
    assertThat(result.language()).isNull();
  }

  @Test
  void languageFilterAppendsQueryParam() throws IOException {
    String html = loadFixture("github/trending-weekly.html");

    mockServer
        .expect(requestTo(Matchers.containsString("language=java")))
        .andRespond(withSuccess(html, MediaType.TEXT_HTML));

    TrendingQuery query = TrendingQuery.fromParams("weekly", 5, "java");
    adapter.fetch(query);

    mockServer.verify();
  }

  @Test
  void serverErrorFallsBackToSearchAdapter() {
    mockServer.expect(requestTo(Matchers.containsString("trending"))).andRespond(withServerError());

    TrendingRepositoriesResult fallbackResult =
        new TrendingRepositoriesResult(List.of(), Instant.now(FIXED_CLOCK), "weekly", null);
    when(fallback.fetch(any())).thenReturn(fallbackResult);

    TrendingQuery query = TrendingQuery.fromParams("weekly", 5, null);
    TrendingRepositoriesResult result = adapter.fetch(query);

    verify(fallback).fetch(query);
    assertThat(result).isSameAs(fallbackResult);
  }

  @Test
  void networkErrorFallsBackToSearchAdapter() {
    mockServer
        .expect(requestTo(Matchers.containsString("trending")))
        .andRespond(
            request -> {
              throw new java.io.IOException("connection reset");
            });

    TrendingRepositoriesResult fallbackResult =
        new TrendingRepositoriesResult(List.of(), Instant.now(FIXED_CLOCK), "weekly", null);
    when(fallback.fetch(any())).thenReturn(fallbackResult);

    TrendingQuery query = TrendingQuery.fromParams("weekly", 5, null);
    TrendingRepositoriesResult result = adapter.fetch(query);

    verify(fallback).fetch(query);
    assertThat(result).isSameAs(fallbackResult);
  }

  @Test
  void emptyParsedResultFallsBackToSearchAdapter() {
    // Respond with HTML that has no article.Box-row items
    String emptyHtml = "<html><body><p>No trending repos</p></body></html>";
    mockServer
        .expect(requestTo(Matchers.containsString("trending")))
        .andRespond(withSuccess(emptyHtml, MediaType.TEXT_HTML));

    TrendingRepositoriesResult fallbackResult =
        new TrendingRepositoriesResult(List.of(), Instant.now(FIXED_CLOCK), "weekly", null);
    when(fallback.fetch(any())).thenReturn(fallbackResult);

    TrendingQuery query = TrendingQuery.fromParams("weekly", 5, null);
    TrendingRepositoriesResult result = adapter.fetch(query);

    verify(fallback).fetch(query);
    assertThat(result).isSameAs(fallbackResult);
  }

  // --- helpers ---

  private static String loadFixture(String path) throws IOException {
    ClassPathResource resource = new ClassPathResource(path);
    return resource.getContentAsString(StandardCharsets.UTF_8);
  }
}
