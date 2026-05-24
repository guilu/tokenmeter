package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisPersistenceService;
import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.domain.analyzer.LanguageStatistics;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotId;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaAnalysisPersistenceService implements AnalysisPersistenceService {
  private final AnalysisJpaRepository repository;

  public JpaAnalysisPersistenceService(AnalysisJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public RepositoryAnalysisResult save(RepositoryAnalysisResult result) {
    AnalysisEntity entity = toEntity(result);
    return toResult(repository.save(entity));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RepositoryAnalysisResult> findById(UUID id) {
    return repository.findById(id).map(this::toResult);
  }

  private static AnalysisEntity toEntity(RepositoryAnalysisResult result) {
    UUID id = result.id() == null ? UUID.randomUUID() : result.id();
    Instant createdAt = result.createdAt() == null ? Instant.now() : result.createdAt();
    AnalysisEntity entity =
        new AnalysisEntity(
            id,
            result.repositoryUrl(),
            result.cloneUrl(),
            result.owner(),
            result.name(),
            AnalysisStatus.SUCCESS,
            result.scan().totalFiles(),
            result.scan().totalLines(),
            result.scan().totalBytes(),
            result.tokenization().encoding(),
            result.tokenization().totalTokens(),
            createdAt);

    PricingSnapshotHandle pricing = result.pricing();
    if (pricing != null) {
      entity.setPricingSnapshotId(pricing.id().value());
      entity.setPricingPrimarySource(pricing.primarySource().name());
      entity.setPricingCapturedAt(pricing.capturedAt());
    }

    result
        .scan()
        .languages()
        .forEach(
            (language, statistics) ->
                entity.addLanguage(
                    new LanguageStatsEntity(
                        language,
                        statistics.files(),
                        statistics.lines(),
                        statistics.bytes(),
                        tokensFor(result, language))));
    result
        .costEstimates()
        .forEach(
            estimate ->
                entity.addCostEstimate(
                    new CostEstimateEntity(
                        estimate.provider(),
                        estimate.model(),
                        estimate.mode(),
                        estimate.baseTokens(),
                        estimate.estimatedInputTokens(),
                        estimate.estimatedOutputTokens(),
                        estimate.inputCost(),
                        estimate.outputCost(),
                        estimate.totalCost(),
                        estimate.formula())));
    return entity;
  }

  private static long tokensFor(RepositoryAnalysisResult result, String language) {
    LanguageTokenMetrics tokenMetrics = result.tokenization().languages().get(language);
    return tokenMetrics == null ? 0 : tokenMetrics.tokens();
  }

  private RepositoryAnalysisResult toResult(AnalysisEntity entity) {
    Map<String, LanguageStatistics> languages =
        entity.getLanguages().stream()
            .collect(
                Collectors.toMap(
                    LanguageStatsEntity::getLanguage,
                    language ->
                        new LanguageStatistics(
                            language.getLanguage(),
                            language.getFiles(),
                            language.getLines(),
                            language.getBytes())));
    Map<String, LanguageTokenMetrics> tokenLanguages =
        entity.getLanguages().stream()
            .collect(
                Collectors.toMap(
                    LanguageStatsEntity::getLanguage,
                    language ->
                        new LanguageTokenMetrics(
                            language.getLanguage(), language.getFiles(), language.getTokens())));

    List<ModelCostEstimate> costEstimates =
        entity.getCostEstimates().stream()
            .map(
                estimate ->
                    new ModelCostEstimate(
                        estimate.getProvider(),
                        estimate.getModel(),
                        estimate.getMode(),
                        estimate.getBaseTokens(),
                        estimate.getEstimatedInputTokens(),
                        estimate.getEstimatedOutputTokens(),
                        estimate.getInputCost(),
                        estimate.getOutputCost(),
                        estimate.getTotalCost(),
                        estimate.getFormula()))
            .toList();

    return new RepositoryAnalysisResult(
        entity.getId(),
        entity.getCreatedAt(),
        entity.getRepositoryUrl(),
        entity.getCloneUrl(),
        entity.getOwner(),
        entity.getName(),
        new RepositoryScanResult(
            entity.getTotalFiles(),
            entity.getTotalLines(),
            entity.getTotalBytes(),
            List.of(),
            languages),
        new RepositoryTokenizationResult(
            entity.getTokenEncoding(),
            entity.getTotalFiles(),
            entity.getTotalTokens(),
            List.of(),
            tokenLanguages),
        costEstimates,
        toPricingHandle(entity));
  }

  private static PricingSnapshotHandle toPricingHandle(AnalysisEntity entity) {
    String storedId = entity.getPricingSnapshotId();
    String storedSource = entity.getPricingPrimarySource();
    Instant capturedAt = entity.getPricingCapturedAt();
    if (storedId == null || storedSource == null || capturedAt == null) {
      return null;
    }
    PricingSource source;
    try {
      source = PricingSource.valueOf(storedSource);
    } catch (IllegalArgumentException ex) {
      return null;
    }
    // Snapshots themselves are not reconstructed from the row — only the identity carrier.
    return new PricingSnapshotHandle(
        new PricingSnapshotId(storedId), source, capturedAt, List.of());
  }
}
