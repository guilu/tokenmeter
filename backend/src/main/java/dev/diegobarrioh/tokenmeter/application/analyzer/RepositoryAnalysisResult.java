package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application-layer carrier for a completed analysis run.
 *
 * <p>The optional {@code pricing} handle propagates the snapshot identifier captured at the start
 * of {@code CALCULATING_COSTS} from the worker to the persistence service so the same id lands on
 * both the {@code analysis_job} row and the resulting {@code analysis} row. Legacy code paths (e.g.
 * tests, in-memory persistence) MAY pass {@code null}.
 */
public record RepositoryAnalysisResult(
    UUID id,
    Instant createdAt,
    String repositoryUrl,
    String cloneUrl,
    String owner,
    String name,
    RepositoryScanResult scan,
    RepositoryTokenizationResult tokenization,
    List<ModelCostEstimate> costEstimates,
    PricingSnapshotHandle pricing) {

  public RepositoryAnalysisResult(
      String repositoryUrl,
      String cloneUrl,
      String owner,
      String name,
      RepositoryScanResult scan,
      RepositoryTokenizationResult tokenization) {
    this(null, null, repositoryUrl, cloneUrl, owner, name, scan, tokenization, List.of(), null);
  }

  public RepositoryAnalysisResult(
      String repositoryUrl,
      String cloneUrl,
      String owner,
      String name,
      RepositoryScanResult scan,
      RepositoryTokenizationResult tokenization,
      List<ModelCostEstimate> costEstimates) {
    this(null, null, repositoryUrl, cloneUrl, owner, name, scan, tokenization, costEstimates, null);
  }

  public RepositoryAnalysisResult(
      String repositoryUrl,
      String cloneUrl,
      String owner,
      String name,
      RepositoryScanResult scan,
      RepositoryTokenizationResult tokenization,
      List<ModelCostEstimate> costEstimates,
      PricingSnapshotHandle pricing) {
    this(
        null,
        null,
        repositoryUrl,
        cloneUrl,
        owner,
        name,
        scan,
        tokenization,
        costEstimates,
        pricing);
  }

  public RepositoryAnalysisResult(
      UUID id,
      Instant createdAt,
      String repositoryUrl,
      String cloneUrl,
      String owner,
      String name,
      RepositoryScanResult scan,
      RepositoryTokenizationResult tokenization,
      List<ModelCostEstimate> costEstimates) {
    this(
        id,
        createdAt,
        repositoryUrl,
        cloneUrl,
        owner,
        name,
        scan,
        tokenization,
        costEstimates,
        null);
  }

  public RepositoryAnalysisResult {
    costEstimates = costEstimates == null ? List.of() : List.copyOf(costEstimates);
  }
}
