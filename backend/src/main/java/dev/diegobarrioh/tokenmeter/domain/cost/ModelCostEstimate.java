package dev.diegobarrioh.tokenmeter.domain.cost;

import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import java.math.BigDecimal;
import java.util.Objects;

public record ModelCostEstimate(
    AiProvider provider,
    String model,
    CostEstimationMode mode,
    long baseTokens,
    long estimatedInputTokens,
    long estimatedOutputTokens,
    BigDecimal inputCost,
    BigDecimal outputCost,
    BigDecimal totalCost,
    String formula) {
  public ModelCostEstimate {
    Objects.requireNonNull(provider, "provider is required");
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model is required");
    }
    Objects.requireNonNull(mode, "mode is required");
    if (baseTokens < 0 || estimatedInputTokens < 0 || estimatedOutputTokens < 0) {
      throw new IllegalArgumentException("token estimates must be positive or zero");
    }
    Objects.requireNonNull(inputCost, "input cost is required");
    Objects.requireNonNull(outputCost, "output cost is required");
    Objects.requireNonNull(totalCost, "total cost is required");
    if (formula == null || formula.isBlank()) {
      throw new IllegalArgumentException("formula is required");
    }
    model = model.trim();
    formula = formula.trim();
  }
}
