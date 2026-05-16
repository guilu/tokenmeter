package dev.diegobarrioh.tokenmeter.infrastructure.pricing.litellm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Subset of the LiteLLM {@code model_prices_and_context_window.json} entry that TokenMeter
 * consumes. Every other field upstream sends is ignored so additions never break parsing.
 *
 * <p>Prices are kept as {@link BigDecimal} to preserve the precision LiteLLM publishes (per-token,
 * often with 8+ decimal places). Conversion to "per million" lives in the mapper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LiteLlmModelEntry(
    @JsonProperty("input_cost_per_token") BigDecimal inputCostPerToken,
    @JsonProperty("output_cost_per_token") BigDecimal outputCostPerToken,
    @JsonProperty("litellm_provider") String litellmProvider,
    @JsonProperty("deprecation_date") String deprecationDate) {}
