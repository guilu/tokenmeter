package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.application.cost.EngineeringEffortEstimate;
import dev.diegobarrioh.tokenmeter.application.cost.EngineeringEffortEstimator;
import dev.diegobarrioh.tokenmeter.domain.analyzer.LanguageStatistics;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RepositoryAnalysisMapper {
  private final EngineeringEffortEstimator engineeringEffortEstimator;

  public RepositoryAnalysisMapper(EngineeringEffortEstimator engineeringEffortEstimator) {
    this.engineeringEffortEstimator = engineeringEffortEstimator;
  }

  public RepositoryAnalysisResponse toResponse(RepositoryAnalysisResult result) {
    var scan = result.scan();
    var tokenization = result.tokenization();
    return new RepositoryAnalysisResponse(
        result.id(),
        result.createdAt(),
        result.repositoryUrl(),
        RepositoryAnalysisStatus.SUCCESS,
        new RepositoryAnalysisMetricsResponse(
            scan.totalFiles(),
            scan.totalLines(),
            scan.totalBytes(),
            tokenization.encoding(),
            tokenization.totalTokens(),
            toLanguageMetrics(result)),
        result.costEstimates().stream().map(this::toCostEstimate).toList(),
        toPricingMetadata(result.pricing()));
  }

  static PricingMetadata toPricingMetadata(PricingSnapshotHandle handle) {
    if (handle == null) {
      return null;
    }
    return new PricingMetadata(
        handle.id().value(), handle.primarySource().name(), handle.capturedAt());
  }

  static PricingMetadata toPricingMetadata(AnalysisJobSnapshot.PricingSnapshotCapture pricing) {
    if (pricing == null) {
      return null;
    }
    return new PricingMetadata(
        pricing.snapshotId().value(), pricing.primarySource().name(), pricing.capturedAt());
  }

  private RepositoryAnalysisCostEstimateResponse toCostEstimate(ModelCostEstimate estimate) {
    return new RepositoryAnalysisCostEstimateResponse(
        estimate.provider().configKey(),
        estimate.model(),
        estimate.mode().name().toLowerCase(),
        estimate.baseTokens(),
        estimate.estimatedInputTokens(),
        estimate.estimatedOutputTokens(),
        estimate.inputCost(),
        estimate.outputCost(),
        estimate.totalCost(),
        estimate.formula(),
        toEngineeringEffort(engineeringEffortEstimator.estimate(estimate)));
  }

  private EngineeringEffortEstimateResponse toEngineeringEffort(
      EngineeringEffortEstimate estimate) {
    return new EngineeringEffortEstimateResponse(
        estimate.seniorEngineerHours(),
        estimate.engineeringDays(),
        estimate.manualImplementationEffort(),
        estimate.summary(),
        estimate.formula(),
        new EngineeringEffortAssumptionsResponse(
            estimate.assumptions().tokensPerSeniorEngineerHour(),
            estimate.assumptions().hoursPerEngineeringDay(),
            estimate.assumptions().modeComplexityMultiplier()));
  }

  private Map<String, RepositoryAnalysisLanguageMetricsResponse> toLanguageMetrics(
      RepositoryAnalysisResult result) {
    Map<String, RepositoryAnalysisLanguageMetricsResponse> languageMetrics = new LinkedHashMap<>();
    result
        .scan()
        .languages()
        .forEach(
            (language, statistics) ->
                languageMetrics.put(language, toLanguageMetrics(result, statistics)));
    return Map.copyOf(languageMetrics);
  }

  private RepositoryAnalysisLanguageMetricsResponse toLanguageMetrics(
      RepositoryAnalysisResult result, LanguageStatistics statistics) {
    LanguageTokenMetrics tokenMetrics =
        result.tokenization().languages().get(statistics.language());
    long tokens = tokenMetrics == null ? 0 : tokenMetrics.tokens();
    return new RepositoryAnalysisLanguageMetricsResponse(
        statistics.language(), statistics.files(), statistics.lines(), statistics.bytes(), tokens);
  }
}
