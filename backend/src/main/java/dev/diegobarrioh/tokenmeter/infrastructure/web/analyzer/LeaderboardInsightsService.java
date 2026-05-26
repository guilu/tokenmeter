package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.CostByModeProjection;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LanguageInsightProjection;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LeaderboardJpaRepository;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LeaderboardOverviewProjection;
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
@Transactional(readOnly = true)
public class LeaderboardInsightsService {

  private final LeaderboardJpaRepository leaderboardRepository;

  public LeaderboardInsightsService(LeaderboardJpaRepository leaderboardRepository) {
    this.leaderboardRepository = leaderboardRepository;
  }

  public LeaderboardOverviewResponse getOverview(String mode, String provider, String model) {
    String normalizedMode = LeaderboardFilterNormalizer.normalizeMode(mode);
    String normalizedProvider = LeaderboardFilterNormalizer.normalizeProvider(provider);
    String normalizedModel = LeaderboardFilterNormalizer.normalizeModel(model);

    LeaderboardOverviewProjection overview =
        leaderboardRepository.findOverview(normalizedMode, normalizedProvider, normalizedModel);

    List<CostByModeProjection> costRows =
        leaderboardRepository.findCostsByMode(normalizedProvider, normalizedModel);

    List<CostByModeEntry> costsByMode = new ArrayList<>();
    for (CostByModeProjection row : costRows) {
      costsByMode.add(
          new CostByModeEntry(
              row.getMode() == null ? null : row.getMode().toLowerCase(Locale.ROOT),
              row.getTotalCost(),
              row.getAnalysisCount()));
    }

    Map<String, String> filters = buildFilters(normalizedMode, normalizedProvider, normalizedModel);
    return new LeaderboardOverviewResponse(
        overview.getTotalRepos(),
        overview.getTotalAnalyses(),
        overview.getTotalTokens(),
        overview.getTotalBytes(),
        costsByMode,
        filters.isEmpty() ? null : filters);
  }

  public LeaderboardLanguagesResponse getLanguages() {
    List<LanguageInsightProjection> rows = leaderboardRepository.findTopLanguages();

    long totalTokensAllLanguages = 0L;
    for (LanguageInsightProjection row : rows) {
      totalTokensAllLanguages += row.getTotalTokens();
    }

    List<LanguageInsightEntry> languages = new ArrayList<>();
    for (LanguageInsightProjection row : rows) {
      BigDecimal sharePercent = BigDecimal.ZERO;
      if (totalTokensAllLanguages > 0) {
        sharePercent =
            BigDecimal.valueOf(row.getTotalTokens())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalTokensAllLanguages), 2, RoundingMode.HALF_UP);
      }
      languages.add(
          new LanguageInsightEntry(
              row.getLanguageName(), row.getTotalTokens(), row.getRepoCount(), sharePercent));
    }

    return new LeaderboardLanguagesResponse(languages, totalTokensAllLanguages, null);
  }

  private static Map<String, String> buildFilters(String mode, String provider, String model) {
    Map<String, String> filters = new LinkedHashMap<>();
    if (mode != null) filters.put("mode", mode.toLowerCase(Locale.ROOT));
    if (provider != null) filters.put("provider", provider.toLowerCase(Locale.ROOT));
    if (model != null) filters.put("model", model);
    return filters;
  }
}
