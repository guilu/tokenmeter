package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Wire shape for the optional nested {@code pricing} block carried by analysis read endpoints and
 * the job-status endpoint. Populated only when the underlying row has captured a pricing snapshot
 * identifier; omitted entirely (key absent) otherwise via {@link JsonInclude.Include#NON_NULL} on
 * the parent response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PricingMetadata(String snapshotId, String primarySource, Instant capturedAt) {}
