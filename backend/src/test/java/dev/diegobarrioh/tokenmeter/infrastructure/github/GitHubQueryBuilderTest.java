package dev.diegobarrioh.tokenmeter.infrastructure.github;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.application.repository.TrendingQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class GitHubQueryBuilderTest {

  // Fixed clock pinned to 2026-05-27
  private static final Instant FIXED_INSTANT = Instant.parse("2026-05-27T12:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

  @Test
  void dailySinceProducesTodayMinus1Day() {
    TrendingQuery query = TrendingQuery.fromParams("daily", 12, null);
    String q = GitHubQueryBuilder.build(query, FIXED_CLOCK);

    LocalDate expected = LocalDate.of(2026, 5, 26);
    assertThat(q).contains("pushed:>" + expected);
  }

  @Test
  void weeklySinceProducesTodayMinus7Days() {
    TrendingQuery query = TrendingQuery.fromParams("weekly", 12, null);
    String q = GitHubQueryBuilder.build(query, FIXED_CLOCK);

    LocalDate expected = LocalDate.of(2026, 5, 20);
    assertThat(q).contains("pushed:>" + expected);
  }

  @Test
  void monthlySinceProducesTodayMinus30Days() {
    TrendingQuery query = TrendingQuery.fromParams("monthly", 12, null);
    String q = GitHubQueryBuilder.build(query, FIXED_CLOCK);

    LocalDate expected = LocalDate.of(2026, 4, 27);
    assertThat(q).contains("pushed:>" + expected);
  }

  @Test
  void languageIsLowercased() {
    TrendingQuery query = TrendingQuery.fromParams("weekly", 12, "Java");
    String q = GitHubQueryBuilder.build(query, FIXED_CLOCK);

    assertThat(q).contains("language:java");
  }

  @Test
  void noLanguageClauseWhenLanguageAbsent() {
    TrendingQuery query = TrendingQuery.fromParams("weekly", 12, null);
    String q = GitHubQueryBuilder.build(query, FIXED_CLOCK);

    assertThat(q).doesNotContain("language:");
  }

  @Test
  void limitIsIncludedInParams() {
    TrendingQuery query = TrendingQuery.fromParams("weekly", 5, null);
    // limit is used in the per_page param of the RestClient call, not the q string;
    // builder should still expose limit for callers
    assertThat(query.limit()).isEqualTo(5);
  }

  @Test
  void invalidSinceFallsBackToWeekly() {
    TrendingQuery query = TrendingQuery.fromParams("bogus", 12, null);
    String q = GitHubQueryBuilder.build(query, FIXED_CLOCK);

    LocalDate expected = LocalDate.of(2026, 5, 20);
    assertThat(q).contains("pushed:>" + expected);
  }
}
