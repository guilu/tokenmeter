package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

// Nullable tokenizerId/precision (legacy pre-V10 rows) are omitted from the JSON so the
// contract does not depend on Spring's implicit default-inclusion setting.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CostBreakdownModeResponse(
    String mode,
    long baseTokens,
    long estimatedInputTokens,
    long estimatedOutputTokens,
    BigDecimal inputCost,
    BigDecimal outputCost,
    BigDecimal totalCost,
    String formula,
    String tokenizerId,
    String precision) {}
