package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import java.time.Instant;
import java.util.UUID;

public record RepositoryAnalysisResult(
    UUID id,
    Instant createdAt,
    String repositoryUrl,
    String cloneUrl,
    String owner,
    String name,
    RepositoryScanResult scan,
    RepositoryTokenizationResult tokenization) {
  public RepositoryAnalysisResult(
      String repositoryUrl,
      String cloneUrl,
      String owner,
      String name,
      RepositoryScanResult scan,
      RepositoryTokenizationResult tokenization) {
    this(null, null, repositoryUrl, cloneUrl, owner, name, scan, tokenization);
  }
}
