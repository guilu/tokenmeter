package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

public record RepositoryAnalysisResponse(
    String repositoryUrl,
    RepositoryAnalysisStatus status,
    RepositoryAnalysisMetricsResponse metrics) {}
