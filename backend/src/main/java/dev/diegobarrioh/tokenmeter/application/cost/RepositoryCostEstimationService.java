package dev.diegobarrioh.tokenmeter.application.cost;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.application.tokenizer.ModelTokenizationProfileResolver;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.FileTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenizationPrecision;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class RepositoryCostEstimationService {
  private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

  /**
   * Legacy back-compat tokenizerId used when the old single-long overloads are called. Points to
   * the primary OpenAI o200k tokenizer so legacy estimates are semantically correct.
   */
  private static final String LEGACY_TOKENIZER_ID = FileTokenMetrics.PRIMARY_TOKENIZER_ID;

  private static final TokenizationPrecision LEGACY_PRECISION = TokenizationPrecision.EXACT_LOCAL;

  private static final Comparator<PricingSnapshot> CANONICAL_ORDER =
      Comparator.comparing((PricingSnapshot s) -> s.provider().configKey())
          .thenComparing(s -> s.model().toLowerCase(Locale.ROOT).trim());

  private final PricingProvider pricingProvider;
  private final ModelTokenizationProfileResolver profileResolver;

  public RepositoryCostEstimationService(
      PricingProvider pricingProvider, ModelTokenizationProfileResolver profileResolver) {
    this.pricingProvider = pricingProvider;
    this.profileResolver = profileResolver;
  }

  /**
   * Back-compat overload. Reads the provider snapshot list once at the start of the call, then
   * iterates the captured copy so a {@link
   * dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshedEvent} arriving
   * mid-call cannot change the result.
   *
   * <p>Estimates are tagged with {@code tokenizerId="openai/o200k_base"} and {@code
   * precision=EXACT_LOCAL} (legacy broadcast semantics).
   */
  public List<ModelCostEstimate> estimate(long baseTokens) {
    if (baseTokens < 0) {
      throw new IllegalArgumentException("base tokens must be positive or zero");
    }
    List<PricingSnapshot> captured = List.copyOf(pricingProvider.snapshots());
    return estimateFromSnapshotsLegacy(captured, baseTokens);
  }

  /**
   * Handle-aware back-compat overload. Iterates exactly the snapshots carried by the {@link
   * PricingSnapshotHandle}.
   *
   * <p>Estimates are tagged with {@code tokenizerId="openai/o200k_base"} and {@code
   * precision=EXACT_LOCAL} (legacy broadcast semantics).
   */
  public List<ModelCostEstimate> estimate(long baseTokens, PricingSnapshotHandle handle) {
    if (baseTokens < 0) {
      throw new IllegalArgumentException("base tokens must be positive or zero");
    }
    Objects.requireNonNull(handle, "handle is required");
    return estimateFromSnapshotsLegacy(handle.snapshots(), baseTokens);
  }

  /**
   * New multi-tokenizer overload. For each pricing model, looks up its tokenizer profile via the
   * injected {@link ModelTokenizationProfileResolver}, retrieves the token count keyed by {@code
   * profile.tokenizerId()} from {@code tokensByTokenizerId}, and computes cost using that count.
   *
   * <p>Each returned {@link ModelCostEstimate} carries the real {@code tokenizerId} and {@code
   * precision} from the model's profile. If a tokenizer ID is absent from the map, the base count
   * defaults to 0 (null-safe, no exception).
   *
   * @param tokensByTokenizerId per-tokenizer total counts from {@code
   *     RepositoryTokenizationService.tokenize()}
   * @param handle the pricing snapshot handle captured before tokenization
   * @return one estimate per (pricing model × CostEstimationMode) tuple, sorted canonically
   */
  public List<ModelCostEstimate> estimate(
      Map<String, Long> tokensByTokenizerId, PricingSnapshotHandle handle) {
    Objects.requireNonNull(tokensByTokenizerId, "tokensByTokenizerId is required");
    Objects.requireNonNull(handle, "handle is required");
    return handle.snapshots().stream()
        .sorted(CANONICAL_ORDER)
        .map(PricingSnapshot::pricing)
        .flatMap(
            pricing -> {
              ModelTokenizationProfile profile =
                  profileResolver.resolve(pricing.provider(), pricing.model());
              long base = tokensByTokenizerId.getOrDefault(profile.tokenizerId(), 0L);
              return Arrays.stream(CostEstimationMode.values())
                  .map(
                      mode ->
                          estimate(
                              pricing, mode, base, profile.tokenizerId(), profile.precision()));
            })
        .toList();
  }

  // --- private helpers ---

  private static List<ModelCostEstimate> estimateFromSnapshotsLegacy(
      List<PricingSnapshot> snapshots, long baseTokens) {
    return snapshots.stream()
        .sorted(CANONICAL_ORDER)
        .map(PricingSnapshot::pricing)
        .flatMap(
            pricing ->
                Arrays.stream(CostEstimationMode.values())
                    .map(
                        mode ->
                            estimate(
                                pricing, mode, baseTokens, LEGACY_TOKENIZER_ID, LEGACY_PRECISION)))
        .toList();
  }

  private static ModelCostEstimate estimate(
      ModelPricing pricing,
      CostEstimationMode mode,
      long baseTokens,
      String tokenizerId,
      TokenizationPrecision precision) {
    long estimatedOutputTokens = multiplyCeiling(baseTokens, mode.outputMultiplier());
    long estimatedInputTokens = multiplyCeiling(baseTokens, mode.reasoningInputMultiplier());
    BigDecimal inputCost = cost(estimatedInputTokens, pricing.inputTokenPricePerMillion());
    BigDecimal outputCost = cost(estimatedOutputTokens, pricing.outputTokenPricePerMillion());
    BigDecimal totalCost = inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    return new ModelCostEstimate(
        pricing.provider(),
        pricing.model(),
        mode,
        tokenizerId,
        precision,
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
