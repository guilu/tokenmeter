package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RepositoryAnalysisResult(
    UUID id,
    Instant createdAt,
    String repositoryUrl,
    String cloneUrl,
    String owner,
    String name,
    RepositoryScanResult scan,
    RepositoryTokenizationResult tokenization,
    List<ModelCostEstimate> costEstimates) {
  public RepositoryAnalysisResult(
      String repositoryUrl,
      String cloneUrl,
      String owner,
      String name,
      RepositoryScanResult scan,
      RepositoryTokenizationResult tokenization) {
    this(null, null, repositoryUrl, cloneUrl, owner, name, scan, tokenization, List.of());
  }

  public RepositoryAnalysisResult(
      String repositoryUrl,
      String cloneUrl,
      String owner,
      String name,
      RepositoryScanResult scan,
      RepositoryTokenizationResult tokenization,
      List<ModelCostEstimate> costEstimates) {
    this(null, null, repositoryUrl, cloneUrl, owner, name, scan, tokenization, costEstimates);
  }

  public RepositoryAnalysisResult {
    costEstimates = costEstimates == null ? List.of() : List.copyOf(costEstimates);
  }
}
