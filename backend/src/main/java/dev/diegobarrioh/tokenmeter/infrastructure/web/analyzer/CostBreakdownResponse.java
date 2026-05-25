package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CostBreakdownResponse(
    UUID analysisId,
    Instant createdAt,
    String repositoryUrl,
    CostBreakdownSummaryResponse summary,
    List<CostBreakdownModelResponse> models,
    PricingMetadata pricing) {}
