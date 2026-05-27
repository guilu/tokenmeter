package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LeaderboardJpaRepository;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LeaderboardRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaderboardService {
  private static final int MAX_PAGE_SIZE = 50;

  private final LeaderboardJpaRepository leaderboardRepository;

  public LeaderboardService(LeaderboardJpaRepository leaderboardRepository) {
    this.leaderboardRepository = leaderboardRepository;
  }

  @Transactional(readOnly = true)
  public LeaderboardPageResponse getLeaderboard(
      LeaderboardCategory category,
      int requestedPage,
      int requestedSize,
      String mode,
      String provider,
      String model) {
    int page = Math.max(0, requestedPage);
    int size = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedSize));
    String normalizedMode = LeaderboardFilterNormalizer.normalizeMode(mode);
    String normalizedProvider = LeaderboardFilterNormalizer.normalizeProvider(provider);
    String normalizedModel = LeaderboardFilterNormalizer.normalizeModel(model);

    long totalElements = countFor(category, normalizedMode, normalizedProvider, normalizedModel);
    int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

    List<LeaderboardRow> rows =
        queryFor(
            category,
            normalizedMode,
            normalizedProvider,
            normalizedModel,
            size,
            (long) page * size);

    List<LeaderboardEntryResponse> entries = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      entries.add(toResponse(rows.get(i), page * size + i + 1));
    }

    Map<String, String> filters = buildFilters(normalizedMode, normalizedProvider, normalizedModel);
    return new LeaderboardPageResponse(
        category.slug(), page, size, totalElements, totalPages, filters, entries);
  }

  private long countFor(LeaderboardCategory category, String mode, String provider, String model) {
    return switch (category) {
      case MOST_ANALYZED -> leaderboardRepository.countDistinctRepositories();
      case LARGEST, HIGHEST_TOKEN_COUNT -> leaderboardRepository.countDistinctRepositories();
      case MOST_EXPENSIVE, CHEAPEST, BEST_COST_EFFICIENCY ->
          leaderboardRepository.countCostFiltered(mode, provider, model);
    };
  }

  private List<LeaderboardRow> queryFor(
      LeaderboardCategory category,
      String mode,
      String provider,
      String model,
      int limit,
      long offset) {
    return switch (category) {
      case MOST_EXPENSIVE ->
          leaderboardRepository.findMostExpensive(mode, provider, model, limit, offset);
      case CHEAPEST -> leaderboardRepository.findCheapest(mode, provider, model, limit, offset);
      case BEST_COST_EFFICIENCY ->
          leaderboardRepository.findBestCostEfficiency(mode, provider, model, limit, offset);
      case LARGEST -> leaderboardRepository.findLargest(mode, provider, model, limit, offset);
      case HIGHEST_TOKEN_COUNT ->
          leaderboardRepository.findHighestTokenCount(mode, provider, model, limit, offset);
      case MOST_ANALYZED -> leaderboardRepository.findMostAnalyzed(limit, offset);
    };
  }

  private static LeaderboardEntryResponse toResponse(LeaderboardRow row, int rank) {
    BigDecimal totalCost = row.getTotalCost();
    BigDecimal costPerMillionTokens = null;
    if (totalCost != null && row.getTotalTokens() > 0) {
      costPerMillionTokens =
          totalCost
              .multiply(BigDecimal.valueOf(1_000_000))
              .divide(BigDecimal.valueOf(row.getTotalTokens()), 6, RoundingMode.HALF_UP);
    }
    return new LeaderboardEntryResponse(
        rank,
        row.getId(),
        row.getRepositoryUrl(),
        row.getOwnerName(),
        row.getRepositoryName(),
        row.getCreatedAt(),
        row.getTotalFiles(),
        row.getTotalLines(),
        row.getTotalBytes(),
        row.getTotalTokens(),
        row.getAnalysisCount(),
        row.getProvider() == null ? null : row.getProvider().toLowerCase(Locale.ROOT),
        row.getModel(),
        row.getMode() == null ? null : row.getMode().toLowerCase(Locale.ROOT),
        totalCost,
        costPerMillionTokens,
        toPricingMetadata(row),
        row.getDominantLanguage());
  }

  private static PricingMetadata toPricingMetadata(LeaderboardRow row) {
    if (row.getPricingSnapshotId() == null) {
      return null;
    }
    return new PricingMetadata(
        row.getPricingSnapshotId(), row.getPricingPrimarySource(), row.getPricingCapturedAt());
  }

  private static Map<String, String> buildFilters(String mode, String provider, String model) {
    Map<String, String> filters = new LinkedHashMap<>();
    if (mode != null) filters.put("mode", mode.toLowerCase(Locale.ROOT));
    if (provider != null) filters.put("provider", provider.toLowerCase(Locale.ROOT));
    if (model != null) filters.put("model", model);
    return filters;
  }
}
