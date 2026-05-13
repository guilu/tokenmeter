package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.util.Arrays;

public enum LeaderboardCategory {
  MOST_EXPENSIVE("most-expensive"),
  CHEAPEST("cheapest"),
  LARGEST("largest"),
  MOST_ANALYZED("most-analyzed"),
  HIGHEST_TOKEN_COUNT("highest-token-count"),
  BEST_COST_EFFICIENCY("best-cost-efficiency");

  private final String slug;

  LeaderboardCategory(String slug) {
    this.slug = slug;
  }

  public String slug() {
    return slug;
  }

  public static LeaderboardCategory fromSlug(String slug) {
    return Arrays.stream(values())
        .filter(category -> category.slug.equalsIgnoreCase(slug))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unsupported leaderboard category: " + slug));
  }
}
