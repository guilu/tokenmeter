package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.time.Instant;
import java.util.UUID;

public record RepositoryAnalysisResponse(
    UUID id,
    Instant createdAt,
    String repositoryUrl,
    RepositoryAnalysisStatus status,
    RepositoryAnalysisMetricsResponse metrics) {}
