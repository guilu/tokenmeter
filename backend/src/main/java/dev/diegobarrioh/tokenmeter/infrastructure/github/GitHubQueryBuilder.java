package dev.diegobarrioh.tokenmeter.infrastructure.github;

import dev.diegobarrioh.tokenmeter.application.repository.TrendingQuery;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Builds the GitHub Search API {@code q} parameter string for trending repository queries.
 *
 * <p>Query format: {@code stars:>10 pushed:>{ISO_DATE} [language:{lang}]}
 *
 * <p>Sorting and pagination ({@code sort}, {@code order}, {@code per_page}) are separate query
 * parameters appended by the adapter.
 */
public final class GitHubQueryBuilder {

  private GitHubQueryBuilder() {}

  /**
   * Builds the {@code q} search string. The pushed-after date is computed from {@code clock} so
   * tests can use a fixed instant.
   */
  public static String build(TrendingQuery query, Clock clock) {
    LocalDate cutoff = LocalDate.now(clock).minusDays(query.since().days());
    StringBuilder sb = new StringBuilder();
    sb.append("stars:>10 pushed:>").append(cutoff);
    query.language().ifPresent(lang -> sb.append(" language:").append(lang));
    return sb.toString();
  }
}
