package dev.diegobarrioh.tokenmeter.infrastructure.web.pricing;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * JSON view of a single pricing entry returned by {@code GET /api/pricing}. The first four fields
 * preserve the legacy contract; {@code source}, {@code fetchedAt} and the optional {@code
 * externalModelId} surface freshness metadata introduced by the dynamic pricing pipeline.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PricingModelResponse(
    String provider,
    String model,
    BigDecimal inputTokenPricePerMillion,
    BigDecimal outputTokenPricePerMillion,
    String source,
    OffsetDateTime fetchedAt,
    String externalModelId) {}
