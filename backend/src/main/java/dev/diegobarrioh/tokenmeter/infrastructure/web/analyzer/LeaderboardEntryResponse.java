package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
    BigDecimal costPerMillionTokens,
    PricingMetadata pricing,
    String dominantLanguage) {}
