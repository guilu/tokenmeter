package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.PricingMappingLoader.MappingKey;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.litellm.LiteLlmModelEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Transforms the raw LiteLLM catalogue into {@link PricingSnapshot} values tagged {@link
 * PricingSource#REMOTE}. Pure conversion logic — no HTTP, no JPA, no time travel beyond the
 * supplied {@code fetchedAt}.
 *
 * <p>Iteration walks the configured mapping (not the raw payload) so missing upstream keys are
 * explicitly logged instead of silently dropped.
 */
@Component
public class LiteLlmPricingMapper {

  private static final Logger LOG = LoggerFactory.getLogger(LiteLlmPricingMapper.class);
  private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
  private static final int PRICE_SCALE = 6;

  private final PricingMappingLoader mappingLoader;

  public LiteLlmPricingMapper(PricingMappingLoader mappingLoader) {
    this.mappingLoader = mappingLoader;
  }

  /**
   * Walks every configured mapping and produces a snapshot when the upstream entry has positive
   * input + output prices. Mapping misses (key absent upstream or null/non-positive prices) are
   * logged at WARN and skipped — pre-existing rows in {@code model_pricing} are preserved by the
   * caller's choice of {@code replaceRemote} semantics.
   */
  public List<PricingSnapshot> mapToSnapshots(
      Map<String, LiteLlmModelEntry> raw, OffsetDateTime fetchedAt) {
    if (raw == null) {
      throw new IllegalArgumentException("raw payload is required");
    }
    if (fetchedAt == null) {
      throw new IllegalArgumentException("fetchedAt is required");
    }

    Map<MappingKey, String> mappings = new LinkedHashMap<>(mappingLoader.mappings());
    List<PricingSnapshot> snapshots = new ArrayList<>(mappings.size());

    for (Map.Entry<MappingKey, String> mapping : mappings.entrySet()) {
      MappingKey key = mapping.getKey();
      String litellmKey = mapping.getValue();

      LiteLlmModelEntry entry = raw.get(litellmKey);
      if (entry == null) {
        LOG.warn(
            "LiteLLM key not found: {} (internal {}:{})",
            litellmKey,
            key.provider().configKey(),
            key.normalizedModel());
        continue;
      }
      if (!isPositive(entry.inputCostPerToken()) || !isPositive(entry.outputCostPerToken())) {
        LOG.warn(
            "litellm entry {} has null/non-positive price, skipping (input={} output={})",
            litellmKey,
            entry.inputCostPerToken(),
            entry.outputCostPerToken());
        continue;
      }

      BigDecimal inputPerMillion = pricePerMillion(entry.inputCostPerToken());
      BigDecimal outputPerMillion = pricePerMillion(entry.outputCostPerToken());

      ModelPricing pricing =
          new ModelPricing(
              key.provider(), key.normalizedModel(), inputPerMillion, outputPerMillion);
      snapshots.add(new PricingSnapshot(pricing, PricingSource.REMOTE, fetchedAt, litellmKey));
    }

    return List.copyOf(snapshots);
  }

  private static BigDecimal pricePerMillion(BigDecimal costPerToken) {
    return ONE_MILLION.multiply(costPerToken).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
  }

  private static boolean isPositive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }
}
