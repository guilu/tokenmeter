package dev.diegobarrioh.tokenmeter.infrastructure.github;

import dev.diegobarrioh.tokenmeter.application.repository.TrendingQuery;
import dev.diegobarrioh.tokenmeter.application.repository.TrendingRepositoriesPort;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepositoriesResult;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Primary implementation of {@link TrendingRepositoriesPort} that scrapes the GitHub Trending page
 * ({@code https://github.com/trending}) using jsoup.
 *
 * <p>This adapter returns repositories ranked by stars gained in the selected period — the real
 * "trending" signal — rather than by total stars (which the Search API fallback uses).
 *
 * <p>Fallback contract: any failure (HTTP error, parse exception, zero parsed items) is logged at
 * WARN and the request is transparently delegated to {@link GitHubSearchAdapter}. The section is
 * never broken by a scraping failure.
 */
@Primary
@Component
public class GitHubTrendingScrapeAdapter implements TrendingRepositoriesPort {

  private static final Logger LOG = LoggerFactory.getLogger(GitHubTrendingScrapeAdapter.class);

  private static final Pattern PERIOD_STARS_PATTERN =
      Pattern.compile(
          "^([\\d,]+)\\s+stars\\s+(today|this week|this month)", Pattern.CASE_INSENSITIVE);

  private final RestClient trendingClient;
  private final GitHubSearchAdapter fallback;
  private final Clock clock;

  public GitHubTrendingScrapeAdapter(
      @Qualifier("gitHubTrendingClient") RestClient trendingClient,
      GitHubSearchAdapter fallback,
      Clock clock) {
    this.trendingClient = trendingClient;
    this.fallback = fallback;
    this.clock = clock;
  }

  @Override
  public TrendingRepositoriesResult fetch(TrendingQuery query) {
    String url = buildUrl(query);
    LOG.debug(
        "Scraping GitHub Trending: since={}, limit={}, language={}",
        query.since(),
        Integer.valueOf(query.limit()),
        query.language().orElse("(none)"));

    try {
      String html =
          trendingClient
              .get()
              .uri(url)
              .retrieve()
              .onStatus(
                  status -> !status.is2xxSuccessful(),
                  (req, resp) -> {
                    throw new RestClientException(
                        "GitHub Trending returned non-2xx: " + resp.getStatusCode());
                  })
              .body(String.class);

      if (html == null || html.isBlank()) {
        LOG.warn("GitHub Trending returned empty body, falling back to Search API");
        return fallback.fetch(query);
      }

      List<TrendingRepository> items = parse(html, query);

      if (items.isEmpty()) {
        LOG.warn(
            "GitHub Trending page parsed zero items (HTML structure may have changed), falling back"
                + " to Search API");
        return fallback.fetch(query);
      }

      LOG.debug("Scraped {} trending repositories from GitHub Trending page", items.size());
      return new TrendingRepositoriesResult(
          items,
          Instant.now(clock),
          query.since().name().toLowerCase(),
          query.language().orElse(null));

    } catch (RestClientException e) {
      LOG.warn("GitHub Trending scrape HTTP error: {}, falling back to Search API", e.getMessage());
      return fallback.fetch(query);
    } catch (Exception e) {
      LOG.warn("GitHub Trending scrape failed: {}, falling back to Search API", e.getMessage());
      return fallback.fetch(query);
    }
  }

  /**
   * Parses the GitHub Trending HTML page and extracts up to {@code query.limit()} repositories.
   * Package-private to allow unit testing without HTTP.
   */
  static List<TrendingRepository> parse(String html, TrendingQuery query) {
    Document doc = Jsoup.parse(html);
    Elements articles = doc.select("article.Box-row");

    List<TrendingRepository> result = new ArrayList<>();
    for (Element article : articles) {
      if (result.size() >= query.limit()) {
        break;
      }
      TrendingRepository repo = parseArticle(article);
      if (repo != null) {
        result.add(repo);
      }
    }
    return result;
  }

  private static TrendingRepository parseArticle(Element article) {
    Element h2link = article.selectFirst("h2 a");
    if (h2link == null) {
      return null;
    }

    String href = h2link.attr("href").trim();
    // href format: "/owner/repo" – strip leading slash and normalise whitespace/newlines
    String fullName = href.startsWith("/") ? href.substring(1) : href;
    fullName = fullName.replaceAll("[\\s\\n]+", "");
    if (fullName.isBlank()) {
      return null;
    }
    String htmlUrl = "https://github.com/" + fullName;

    // Description
    Element descEl = article.selectFirst("p.col-9");
    String description = descEl != null ? descEl.text().trim() : null;
    if (description != null && description.isBlank()) {
      description = null;
    }

    // Language
    Element langEl = article.selectFirst("span[itemprop=programmingLanguage]");
    String language = langEl != null ? langEl.text().trim() : null;
    if (language != null && language.isBlank()) {
      language = null;
    }

    // Total stars
    Element starsEl = article.selectFirst("a[href$=/stargazers]");
    int stars = starsEl != null ? parseCount(starsEl.text()) : 0;

    // Total forks
    Element forksEl = article.selectFirst("a[href$=/forks]");
    int forks = forksEl != null ? parseCount(forksEl.text()) : 0;

    // Stars this period (e.g. "18,703 stars this week")
    Element periodEl = article.selectFirst("span.d-inline-block.float-sm-right");
    Integer starsThisPeriod = null;
    if (periodEl != null) {
      starsThisPeriod = parsePeriodStars(periodEl.text().trim());
    }

    return new TrendingRepository(
        fullName, htmlUrl, description, language, stars, forks, null, null, null, starsThisPeriod);
  }

  private static int parseCount(String text) {
    if (text == null) {
      return 0;
    }
    try {
      return Integer.parseInt(text.replaceAll("[,\\s]", "").trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static Integer parsePeriodStars(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    Matcher m = PERIOD_STARS_PATTERN.matcher(text);
    if (m.find()) {
      try {
        return Integer.parseInt(m.group(1).replace(",", ""));
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  private static String buildUrl(TrendingQuery query) {
    String since = query.since().name().toLowerCase();
    StringBuilder sb = new StringBuilder("/trending?since=").append(since);
    sb.append("&spoken_language_code=");
    query.language().ifPresent(lang -> sb.append("&language=").append(lang));
    return sb.toString();
  }
}
