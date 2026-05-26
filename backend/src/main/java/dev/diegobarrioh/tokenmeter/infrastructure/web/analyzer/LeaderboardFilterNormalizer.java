package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import java.util.Locale;

/** Package-private utility for normalizing leaderboard filter parameters. */
class LeaderboardFilterNormalizer {

  private LeaderboardFilterNormalizer() {}

  static String normalizeMode(String value) {
    if (value == null || value.isBlank()) return null;
    String upper = value.trim().toUpperCase(Locale.ROOT);
    try {
      CostEstimationMode.valueOf(upper);
      return upper;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  static String normalizeProvider(String value) {
    if (value == null || value.isBlank()) return null;
    String upper = value.trim().toUpperCase(Locale.ROOT);
    try {
      AiProvider.valueOf(upper);
      return upper;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  static String normalizeModel(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }
}
