package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import java.util.UUID;

/**
 * Body of the synchronous {@code 202 Accepted} reply to {@code POST /api/analyze}. {@code
 * analysisId} is always {@code null} in this capability and is reserved for a future fast-path that
 * may return an existing analysis.
 */
public record AnalysisJobAcceptedResponse(
    String jobId, AnalysisJobStatus status, String statusUrl, UUID analysisId) {}
