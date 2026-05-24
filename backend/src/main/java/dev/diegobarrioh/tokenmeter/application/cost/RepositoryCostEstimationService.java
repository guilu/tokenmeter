package dev.diegobarrioh.tokenmeter.application.cost;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class RepositoryCostEstimationService {
  private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

  private static final Comparator<PricingSnapshot> CANONICAL_ORDER =
      Comparator.comparing((PricingSnapshot s) -> s.provider().configKey())
          .thenComparing(s -> s.model().toLowerCase(Locale.ROOT).trim());

  private final PricingProvider pricingProvider;

  public RepositoryCostEstimationService(PricingProvider pricingProvider) {
    this.pricingProvider = pricingProvider;
  }

  /**
   * Back-compat overload. Reads the provider snapshot list once at the start of the call, then
   * iterates the captured copy so a {@link
   * dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshedEvent} arriving
   * mid-call cannot change the result.
   */
  public List<ModelCostEstimate> estimate(long baseTokens) {
    if (baseTokens < 0) {
      throw new IllegalArgumentException("base tokens must be positive or zero");
    }
    List<PricingSnapshot> captured = List.copyOf(pricingProvider.snapshots());
    return estimateFromSnapshots(captured, baseTokens);
  }

  /**
   * Handle-aware overload. Iterates exactly the snapshots carried by the {@link
   * PricingSnapshotHandle}, ignoring any mutation that may occur on the {@link PricingProvider}
   * cache during the call. Use this overload from the worker so the job and the resulting analysis
   * share a single pricing snapshot id.
   */
  public List<ModelCostEstimate> estimate(long baseTokens, PricingSnapshotHandle handle) {
    if (baseTokens < 0) {
      throw new IllegalArgumentException("base tokens must be positive or zero");
    }
    Objects.requireNonNull(handle, "handle is required");
    return estimateFromSnapshots(handle.snapshots(), baseTokens);
  }

  private static List<ModelCostEstimate> estimateFromSnapshots(
      List<PricingSnapshot> snapshots, long baseTokens) {
    return snapshots.stream()
        .sorted(CANONICAL_ORDER)
        .map(PricingSnapshot::pricing)
        .flatMap(
            pricing ->
                Arrays.stream(CostEstimationMode.values())
                    .map(mode -> estimate(pricing, mode, baseTokens)))
        .toList();
  }

  private static ModelCostEstimate estimate(
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
