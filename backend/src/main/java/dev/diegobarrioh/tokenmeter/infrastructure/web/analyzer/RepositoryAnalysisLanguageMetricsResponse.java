package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

public record RepositoryAnalysisLanguageMetricsResponse(
    String language, long files, long lines, long bytes, long tokens) {}
