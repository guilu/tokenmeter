package dev.diegobarrioh.tokenmeter.domain.tokenizer;

import java.util.List;
import java.util.Map;

public record RepositoryTokenizationResult(
    String encoding,
    long totalFiles,
    long totalTokens,
    List<FileTokenMetrics> files,
    Map<String, LanguageTokenMetrics> languages) {}
