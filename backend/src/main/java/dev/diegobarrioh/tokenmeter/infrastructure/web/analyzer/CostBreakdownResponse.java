package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CostBreakdownResponse(
    UUID analysisId,
    Instant createdAt,
    String repositoryUrl,
    CostBreakdownSummaryResponse summary,
    List<CostBreakdownModelResponse> models) {}
