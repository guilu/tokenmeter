package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

public record CostBreakdownSummaryResponse(long totalTokens, int totalModels, int totalModes) {}
