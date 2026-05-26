package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaderboardOverviewResponse(
    long totalRepos,
    long totalAnalyses,
    long totalTokens,
    long totalBytes,
    List<CostByModeEntry> costsByMode,
    Map<String, String> filters) {}
