package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.math.BigDecimal;

public record LanguageInsightEntry(
    String language, long totalTokens, long repoCount, BigDecimal sharePercent) {}
