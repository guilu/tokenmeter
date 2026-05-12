package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.util.List;
import java.util.Map;

public record LeaderboardPageResponse(
    String category,
    int page,
    int size,
    long totalElements,
    int totalPages,
    Map<String, String> filters,
    List<LeaderboardEntryResponse> entries) {}
