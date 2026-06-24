package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CostBreakdownMapper {
  private final PricingProvider pricingProvider;

  public CostBreakdownMapper(PricingProvider pricingProvider) {
    this.pricingProvider = pricingProvider;
  }

  public CostBreakdownResponse toResponse(RepositoryAnalysisResult result) {
    List<CostBreakdownModelResponse> models = toModels(result);
    int totalModes = models.stream().mapToInt(model -> model.modes().size()).sum();
    return new CostBreakdownResponse(
        result.id(),
        result.createdAt(),
        result.repositoryUrl(),
        new CostBreakdownSummaryResponse(
            result.tokenization().totalTokens(), models.size(), totalModes),
        models,
        RepositoryAnalysisMapper.toPricingMetadata(result.pricing()));
  }

  private List<CostBreakdownModelResponse> toModels(RepositoryAnalysisResult result) {
    Map<String, List<ModelCostEstimate>> grouped = new LinkedHashMap<>();
    result.costEstimates().stream()
        .sorted(
            Comparator.comparing((ModelCostEstimate estimate) -> estimate.provider().configKey())
                .thenComparing(ModelCostEstimate::model)
                .thenComparing(estimate -> estimate.mode().ordinal()))
        .forEach(
            estimate ->
                grouped
                    .computeIfAbsent(
                        estimate.provider().configKey() + ":" + estimate.model(),
                        key -> new java.util.ArrayList<>())
                    .add(estimate));

    return grouped.values().stream().map(this::toModel).toList();
  }

  private CostBreakdownModelResponse toModel(List<ModelCostEstimate> estimates) {
    ModelCostEstimate first = estimates.getFirst();
    CostBreakdownPricingResponse pricing =
        pricingProvider
            .find(first.provider(), first.model())
            .map(
                modelPricing ->
                    new CostBreakdownPricingResponse(
                        modelPricing.inputTokenPricePerMillion(),
                        modelPricing.outputTokenPricePerMillion()))
            .orElse(null);
    return new CostBreakdownModelResponse(
        first.provider().configKey(),
        first.model(),
        pricing,
        estimates.stream().map(this::toMode).toList());
  }

  private CostBreakdownModeResponse toMode(ModelCostEstimate estimate) {
    return new CostBreakdownModeResponse(
        estimate.mode().name().toLowerCase(),
        estimate.baseTokens(),
        estimate.estimatedInputTokens(),
        estimate.estimatedOutputTokens(),
        estimate.inputCost(),
        estimate.outputCost(),
        estimate.totalCost(),
        estimate.formula(),
        estimate.tokenizerId(),
        estimate.precision() == null ? null : estimate.precision().name());
  }
}
