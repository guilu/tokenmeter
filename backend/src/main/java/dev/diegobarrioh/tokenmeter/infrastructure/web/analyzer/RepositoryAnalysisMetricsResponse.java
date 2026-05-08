package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.util.Map;

public record RepositoryAnalysisMetricsResponse(
    long totalFiles,
    long totalLines,
    long totalBytes,
    String tokenEncoding,
    long totalTokens,
    Map<String, RepositoryAnalysisLanguageMetricsResponse> languages) {}
