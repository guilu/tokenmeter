package dev.diegobarrioh.tokenmeter.application.cost;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RepositoryCostEstimationService {
  private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

  private final PricingProvider pricingProvider;

  public RepositoryCostEstimationService(PricingProvider pricingProvider) {
    this.pricingProvider = pricingProvider;
  }

  public List<ModelCostEstimate> estimate(long baseTokens) {
    if (baseTokens < 0) {
      throw new IllegalArgumentException("base tokens must be positive or zero");
    }
    return pricingProvider.all().stream()
        .sorted(
            Comparator.comparing((ModelPricing pricing) -> pricing.provider().configKey())
                .thenComparing(ModelPricing::model))
        .flatMap(
            pricing ->
                Arrays.stream(CostEstimationMode.values())
                    .map(mode -> estimate(pricing, mode, baseTokens)))
        .toList();
  }

  private ModelCostEstimate estimate(
      ModelPricing pricing, CostEstimationMode mode, long baseTokens) {
    long estimatedOutputTokens = multiplyCeiling(baseTokens, mode.outputMultiplier());
    long estimatedInputTokens = multiplyCeiling(baseTokens, mode.reasoningInputMultiplier());
    BigDecimal inputCost = cost(estimatedInputTokens, pricing.inputTokenPricePerMillion());
    BigDecimal outputCost = cost(estimatedOutputTokens, pricing.outputTokenPricePerMillion());
    BigDecimal totalCost = inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    return new ModelCostEstimate(
        pricing.provider(),
        pricing.model(),
        mode,
        baseTokens,
        estimatedInputTokens,
        estimatedOutputTokens,
        inputCost,
        outputCost,
        totalCost,
        formulaFor(mode));
  }

  private static long multiplyCeiling(long tokens, BigDecimal multiplier) {
    return BigDecimal.valueOf(tokens)
        .multiply(multiplier)
        .setScale(0, RoundingMode.CEILING)
        .longValueExact();
  }

  private static BigDecimal cost(long tokens, BigDecimal pricePerMillion) {
    return BigDecimal.valueOf(tokens)
        .multiply(pricePerMillion)
        .divide(ONE_MILLION, 6, RoundingMode.HALF_UP);
  }

  private static String formulaFor(CostEstimationMode mode) {
    return "inputCost=(baseTokens*%s*inputPricePerMillion)/1_000_000; outputCost=(baseTokens*%s*outputPricePerMillion)/1_000_000; totalCost=inputCost+outputCost"
        .formatted(
            mode.reasoningInputMultiplier().stripTrailingZeros().toPlainString(),
            mode.outputMultiplier().stripTrailingZeros().toPlainString());
  }
}
