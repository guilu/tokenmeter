package dev.diegobarrioh.tokenmeter.domain.analyzer;

import java.util.List;
import java.util.Map;

public record RepositoryScanResult(
    long totalFiles,
    long totalLines,
    long totalBytes,
    List<RepositoryFileMetric> files,
    Map<String, LanguageStatistics> languages) {}
