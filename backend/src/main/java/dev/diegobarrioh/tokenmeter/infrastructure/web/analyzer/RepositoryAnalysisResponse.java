package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepositoryAnalysisResponse(
    UUID id,
    Instant createdAt,
    String repositoryUrl,
    RepositoryAnalysisStatus status,
    RepositoryAnalysisMetricsResponse metrics,
    List<RepositoryAnalysisCostEstimateResponse> costEstimates,
    PricingMetadata pricing) {}
