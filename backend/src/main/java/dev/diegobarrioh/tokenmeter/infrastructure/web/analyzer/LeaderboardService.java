package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.AnalysisEntity;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.AnalysisJpaRepository;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.CostEstimateEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaderboardService {
  private static final int MAX_PAGE_SIZE = 50;

  private final AnalysisJpaRepository repository;

  public LeaderboardService(AnalysisJpaRepository repository) {
    this.repository = repository;
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
    Filter filter = Filter.from(mode, provider, model);

    List<LeaderboardCandidate> candidates = candidatesFor(category, filter);
    long totalElements = candidates.size();
    int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    int fromIndex = Math.min(page * size, candidates.size());
    int toIndex = Math.min(fromIndex + size, candidates.size());

    List<LeaderboardEntryResponse> entries = new ArrayList<>();
    for (int index = fromIndex; index < toIndex; index++) {
      entries.add(candidates.get(index).toResponse(index + 1));
    }

    return new LeaderboardPageResponse(
        category.slug(), page, size, totalElements, totalPages, filter.asMap(), entries);
  }

  private List<LeaderboardCandidate> candidatesFor(LeaderboardCategory category, Filter filter) {
    List<AnalysisEntity> analyses = repository.findAll();
    return switch (category) {
      case MOST_ANALYZED -> mostAnalyzed(analyses);
      case LARGEST ->
          rankedAnalyses(
              analyses,
              filter,
              Comparator.comparingLong(LeaderboardCandidate::totalBytes).reversed());
      case HIGHEST_TOKEN_COUNT ->
          rankedAnalyses(
              analyses,
              filter,
              Comparator.comparingLong(LeaderboardCandidate::totalTokens).reversed());
      case MOST_EXPENSIVE ->
          costRanked(
              analyses,
              filter,
              Comparator.comparing(CostEstimateEntity::getTotalCost).reversed(),
              Comparator.comparing(LeaderboardCandidate::totalCost).reversed());
      case CHEAPEST ->
          costRanked(
              analyses,
              filter,
              Comparator.comparing(CostEstimateEntity::getTotalCost),
              Comparator.comparing(LeaderboardCandidate::totalCost));
      case BEST_COST_EFFICIENCY ->
          costRanked(
              analyses,
              filter,
              Comparator.comparing(CostEstimateEntity::getTotalCost),
              Comparator.comparing(LeaderboardCandidate::costPerMillionTokens));
    };
  }

  private static List<LeaderboardCandidate> rankedAnalyses(
      List<AnalysisEntity> analyses, Filter filter, Comparator<LeaderboardCandidate> comparator) {
    return analyses.stream()
        .map(
            analysis ->
                LeaderboardCandidate.from(analysis, selectedCostEstimate(analysis, filter), 1))
        .sorted(
            comparator.thenComparing(LeaderboardCandidate::analyzedAt, Comparator.reverseOrder()))
        .toList();
  }

  private static List<LeaderboardCandidate> costRanked(
      List<AnalysisEntity> analyses,
      Filter filter,
      Comparator<CostEstimateEntity> costSelector,
      Comparator<LeaderboardCandidate> rankingComparator) {
    return analyses.stream()
        .map(
            analysis ->
                selectedCostEstimate(analysis, filter, costSelector)
                    .map(cost -> LeaderboardCandidate.from(analysis, Optional.of(cost), 1)))
        .flatMap(Optional::stream)
        .sorted(
            rankingComparator.thenComparing(
                LeaderboardCandidate::analyzedAt, Comparator.reverseOrder()))
        .toList();
  }

  private static List<LeaderboardCandidate> mostAnalyzed(List<AnalysisEntity> analyses) {
    Map<String, List<AnalysisEntity>> byRepository =
        analyses.stream()
            .collect(
                Collectors.groupingBy(
                    AnalysisEntity::getRepositoryUrl, LinkedHashMap::new, Collectors.toList()));

    return byRepository.values().stream()
        .map(
            group -> {
              AnalysisEntity latest =
                  group.stream()
                      .max(Comparator.comparing(AnalysisEntity::getCreatedAt))
                      .orElseThrow();
              return LeaderboardCandidate.from(
                  latest, selectedCostEstimate(latest, Filter.empty()), group.size());
            })
        .sorted(
            Comparator.comparingLong(LeaderboardCandidate::analysisCount)
                .reversed()
                .thenComparing(LeaderboardCandidate::analyzedAt, Comparator.reverseOrder()))
        .toList();
  }

  private static Optional<CostEstimateEntity> selectedCostEstimate(
      AnalysisEntity analysis, Filter filter) {
    return selectedCostEstimate(
        analysis, filter, Comparator.comparing(CostEstimateEntity::getTotalCost));
  }

  private static Optional<CostEstimateEntity> selectedCostEstimate(
      AnalysisEntity analysis, Filter filter, Comparator<CostEstimateEntity> comparator) {
    return analysis.getCostEstimates().stream().filter(filter::matches).min(comparator);
  }

  private record Filter(CostEstimationMode mode, AiProvider provider, String model) {
    static Filter empty() {
      return new Filter(null, null, null);
    }

    static Filter from(String mode, String provider, String model) {
      return new Filter(parseMode(mode), parseProvider(provider), blankToNull(model));
    }

    boolean matches(CostEstimateEntity estimate) {
      return (mode == null || estimate.getMode() == mode)
          && (provider == null || estimate.getProvider() == provider)
          && (model == null || estimate.getModel().equalsIgnoreCase(model));
    }

    Map<String, String> asMap() {
      Map<String, String> filters = new LinkedHashMap<>();
      if (mode != null) filters.put("mode", mode.name().toLowerCase(Locale.ROOT));
      if (provider != null) filters.put("provider", provider.name().toLowerCase(Locale.ROOT));
      if (model != null) filters.put("model", model);
      return filters;
    }

    private static CostEstimationMode parseMode(String value) {
      if (value == null || value.isBlank()) return null;
      return CostEstimationMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static AiProvider parseProvider(String value) {
      if (value == null || value.isBlank()) return null;
      return AiProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value.trim();
    }
  }

  private record LeaderboardCandidate(
      AnalysisEntity analysis, CostEstimateEntity costEstimate, long analysisCount) {
    static LeaderboardCandidate from(
        AnalysisEntity analysis, Optional<CostEstimateEntity> costEstimate, long analysisCount) {
      return new LeaderboardCandidate(analysis, costEstimate.orElse(null), analysisCount);
    }

    long totalBytes() {
      return analysis.getTotalBytes();
    }

    long totalTokens() {
      return analysis.getTotalTokens();
    }

    java.time.Instant analyzedAt() {
      return analysis.getCreatedAt();
    }

    BigDecimal totalCost() {
      return costEstimate == null ? BigDecimal.ZERO : costEstimate.getTotalCost();
    }

    BigDecimal costPerMillionTokens() {
      if (analysis.getTotalTokens() == 0 || costEstimate == null) return BigDecimal.ZERO;
      return costEstimate
          .getTotalCost()
          .multiply(BigDecimal.valueOf(1_000_000))
          .divide(BigDecimal.valueOf(analysis.getTotalTokens()), 6, RoundingMode.HALF_UP);
    }

    LeaderboardEntryResponse toResponse(int rank) {
      return new LeaderboardEntryResponse(
          rank,
          analysis.getId(),
          analysis.getRepositoryUrl(),
          analysis.getOwner(),
          analysis.getName(),
          analysis.getCreatedAt(),
          analysis.getTotalFiles(),
          analysis.getTotalLines(),
          analysis.getTotalBytes(),
          analysis.getTotalTokens(),
          analysisCount,
          costEstimate == null ? null : costEstimate.getProvider().name().toLowerCase(Locale.ROOT),
          costEstimate == null ? null : costEstimate.getModel(),
          costEstimate == null ? null : costEstimate.getMode().name().toLowerCase(Locale.ROOT),
          costEstimate == null ? null : costEstimate.getTotalCost(),
          costEstimate == null ? null : costPerMillionTokens());
    }
  }
}
