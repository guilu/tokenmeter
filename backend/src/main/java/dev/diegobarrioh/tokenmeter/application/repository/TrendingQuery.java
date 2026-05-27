package dev.diegobarrioh.tokenmeter.application.repository;

import java.util.Locale;
import java.util.Optional;

/**
 * Application-layer query value object for trending repository requests. Encapsulates normalised
 * parameter values with coercion so callers (controller, cache) never see raw strings.
 *
 * <p>{@link Since} mirrors the {@code since} API param. {@link #limit} is clamped to [1, 30].
 * {@link #language} is lowercased and absent when null/blank.
 */
public record TrendingQuery(Since since, int limit, Optional<String> language) {

  /** Granularity of the "recently pushed" date window used by the GitHub Search query. */
  public enum Since {
    DAILY,
    WEEKLY,
    MONTHLY;

    /** Number of days to subtract from today to compute the pushed-after date. */
    public int days() {
      return switch (this) {
        case DAILY -> 1;
        case WEEKLY -> 7;
        case MONTHLY -> 30;
      };
    }

    /** Parses a param string case-insensitively; falls back to {@link #WEEKLY} on invalid input. */
    public static Since fromParam(String value) {
      if (value == null || value.isBlank()) {
        return WEEKLY;
      }
      try {
        return Since.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return WEEKLY;
      }
    }
  }

  private static final int MIN_LIMIT = 1;
  private static final int MAX_LIMIT = 30;
  private static final int DEFAULT_LIMIT = 12;

  /**
   * Factory with full coercion: invalid {@code since} → {@code WEEKLY}; {@code limit} clamped to
   * [1, 30]; {@code language} trimmed, lowercased, empty/null → absent.
   */
  public static TrendingQuery fromParams(String since, int limit, String language) {
    Since resolvedSince = Since.fromParam(since);
    int resolvedLimit = Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, limit));
    Optional<String> resolvedLanguage =
        (language == null || language.isBlank())
            ? Optional.empty()
            : Optional.of(language.trim().toLowerCase(Locale.ROOT));
    return new TrendingQuery(resolvedSince, resolvedLimit, resolvedLanguage);
  }

  /** Default query: weekly, 12 items, no language filter. */
  public static TrendingQuery defaults() {
    return new TrendingQuery(Since.WEEKLY, DEFAULT_LIMIT, Optional.empty());
  }
}
