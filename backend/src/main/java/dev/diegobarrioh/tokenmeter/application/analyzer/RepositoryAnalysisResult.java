package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;

public record RepositoryAnalysisResult(
    String repositoryUrl,
    String cloneUrl,
    String owner,
    String name,
    RepositoryScanResult scan,
    RepositoryTokenizationResult tokenization) {}
