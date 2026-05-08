package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.domain.analyzer.LanguageStatistics;
import java.util.Map;

public record RepositoryAnalysisMetricsResponse(
    long totalFiles, long totalLines, long totalBytes, Map<String, LanguageStatistics> languages) {}
