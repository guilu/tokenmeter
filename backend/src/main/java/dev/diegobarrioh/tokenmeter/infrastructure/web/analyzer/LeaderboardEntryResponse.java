package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LeaderboardEntryResponse(
    int rank,
    UUID analysisId,
    String repositoryUrl,
    String owner,
    String name,
    Instant analyzedAt,
    long totalFiles,
    long totalLines,
    long totalBytes,
    long totalTokens,
    long analysisCount,
    String provider,
    String model,
    String mode,
    BigDecimal totalCost,
    BigDecimal costPerMillionTokens) {}
