package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.PricingMappingLoader.MappingKey;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.litellm.LiteLlmModelEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Transforms the raw LiteLLM catalogue into {@link PricingSnapshot} values tagged {@link
 * PricingSource#REMOTE}. Pure conversion logic — no HTTP, no JPA, no time travel beyond the
 * supplied {@code fetchedAt}.
 *
 * <p>Since TKM-65 iteration walks the full remote payload (not the configured whitelist): every
 * upstream entry with a supported provider and valid pricing is imported, so newly published models
 * appear automatically. {@code pricing-mapping.yaml} is now an override/alias mechanism — when an
 * upstream key is configured there, its canonical {@code (provider, model)} pair wins; otherwise
 * the provider is resolved from {@code litellm_provider} and the model name is derived from the
 * upstream key.
 *
 * <p>Auto-discovered (non-override) entries are additionally filtered by {@link LiteLlmModelFilter}
 * (TKM-66) so only canonical/active text models are imported — dated snapshots, fine-tuned models,
 * non-text modalities, {@code -latest} aliases and preview/beta builds are dropped. Models whose
 * {@code deprecation_date} has already passed are dropped too (TKM-68). Overrides bypass these
 * filters. Malformed, unsupported, non-canonical and deprecated entries are skipped and counted.
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
   * Iterates every upstream entry and produces a snapshot when it has positive input + output
   * prices and a supported provider (resolved from a configured override or {@code
   * litellm_provider}). Entries that are malformed (null/non-positive price) or belong to an
   * unsupported provider are skipped and reflected in {@link MappingResult#skipped()}. Entries that
   * normalize to an already-seen {@code (provider, model)} pair are de-duplicated, first wins.
   */
  public MappingResult mapToSnapshots(
      Map<String, LiteLlmModelEntry> raw, OffsetDateTime fetchedAt) {
    if (raw == null) {
      throw new IllegalArgumentException("raw payload is required");
    }
    if (fetchedAt == null) {
      throw new IllegalArgumentException("fetchedAt is required");
    }

    Map<String, MappingKey> overrides = reverseOverrides();
    Map<MappingKey, PricingSnapshot> snapshots = new LinkedHashMap<>();
    // Tracked separately for operator visibility, summed into the single MappingResult#skipped
    // count:
    //  - malformed: null/non-positive price (actionable data-quality signal);
    //  - unsupportedProvider: litellm_provider outside the allowlist (expected, large);
    //  - nonCanonical: dated/fine-tuned/modality/preview variants filtered out (TKM-66, expected).
    //  - deprecated: deprecation_date already in the past as of fetchedAt (TKM-68).
    int skippedMalformed = 0;
    int skippedUnsupported = 0;
    int skippedNonCanonical = 0;
    int skippedDeprecated = 0;

    for (Map.Entry<String, LiteLlmModelEntry> upstream : raw.entrySet()) {
      String litellmKey = upstream.getKey();
      LiteLlmModelEntry entry = upstream.getValue();

      if (entry == null
          || !isPositive(entry.inputCostPerToken())
          || !isPositive(entry.outputCostPerToken())) {
        LOG.debug("Skipping litellm entry {} with null/non-positive price", litellmKey);
        skippedMalformed++;
        continue;
      }

      MappingKey key;
      MappingKey override = overrides.get(litellmKey);
      if (override != null) {
        // Explicit override always wins — bypasses provider allowlist and canonical filter.
        key = override;
      } else {
        AiProvider provider = AiProvider.fromLiteLlmProvider(entry.litellmProvider()).orElse(null);
        if (provider == null) {
          LOG.debug(
              "Skipping litellm entry {} with unsupported provider {}",
              litellmKey,
              entry.litellmProvider());
          skippedUnsupported++;
          continue;
        }
        if (!LiteLlmModelFilter.isCanonical(litellmKey)) {
          LOG.debug("Skipping non-canonical litellm entry {}", litellmKey);
          skippedNonCanonical++;
          continue;
        }
        if (isDeprecated(entry.deprecationDate(), fetchedAt)) {
          LOG.debug(
              "Skipping deprecated litellm entry {} (deprecation_date={})",
              litellmKey,
              entry.deprecationDate());
          skippedDeprecated++;
          continue;
        }
        key = new MappingKey(provider, deriveModel(litellmKey));
      }

      if (snapshots.containsKey(key)) {
        LOG.debug("Skipping duplicate litellm entry {} for {}", litellmKey, key);
        continue;
      }

      BigDecimal inputPerMillion = pricePerMillion(entry.inputCostPerToken());
      BigDecimal outputPerMillion = pricePerMillion(entry.outputCostPerToken());
      ModelPricing pricing =
          new ModelPricing(
              key.provider(), key.normalizedModel(), inputPerMillion, outputPerMillion);
      snapshots.put(key, new PricingSnapshot(pricing, PricingSource.REMOTE, fetchedAt, litellmKey));
    }

    int skipped = skippedMalformed + skippedUnsupported + skippedNonCanonical + skippedDeprecated;
    LOG.info(
        "LiteLLM mapping: imported={} skipped={} (malformedPrice={} unsupportedProvider={} "
            + "nonCanonical={} deprecated={}) of {} upstream entries",
        Integer.valueOf(snapshots.size()),
        Integer.valueOf(skipped),
        Integer.valueOf(skippedMalformed),
        Integer.valueOf(skippedUnsupported),
        Integer.valueOf(skippedNonCanonical),
        Integer.valueOf(skippedDeprecated),
        Integer.valueOf(raw.size()));
    return new MappingResult(List.copyOf(snapshots.values()), skipped);
  }

  private Map<String, MappingKey> reverseOverrides() {
    Map<String, MappingKey> reverse = new LinkedHashMap<>();
    for (Map.Entry<MappingKey, String> entry : mappingLoader.mappings().entrySet()) {
      reverse.putIfAbsent(entry.getValue(), entry.getKey());
    }
    return reverse;
  }

  /**
   * Returns {@code true} when {@code deprecationDate} is an ISO date that is on or before the
   * {@code fetchedAt} date (already deprecated). Blank, future or unparseable dates are treated as
   * NOT deprecated so a malformed upstream field never silently drops an active model (TKM-68).
   */
  private static boolean isDeprecated(String deprecationDate, OffsetDateTime fetchedAt) {
    if (deprecationDate == null || deprecationDate.isBlank()) {
      return false;
    }
    try {
      LocalDate deprecatedOn = LocalDate.parse(deprecationDate.trim());
      return !deprecatedOn.isAfter(fetchedAt.toLocalDate());
    } catch (DateTimeParseException ex) {
      LOG.debug("Unparseable deprecation_date '{}', treating as active", deprecationDate);
      return false;
    }
  }

  /** Strips a {@code provider/} prefix (e.g. {@code gemini/gemini-2.5-pro}) and normalizes. */
  private static String deriveModel(String litellmKey) {
    int slash = litellmKey.lastIndexOf('/');
    String name = slash >= 0 ? litellmKey.substring(slash + 1) : litellmKey;
    return PricingMappingLoader.normalizeModel(name);
  }

  private static BigDecimal pricePerMillion(BigDecimal costPerToken) {
    return ONE_MILLION.multiply(costPerToken).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
  }

  private static boolean isPositive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }

  /**
   * Outcome of a mapping run: the imported snapshots plus the count of skipped upstream entries.
   */
  public record MappingResult(List<PricingSnapshot> snapshots, int skipped) {

    public MappingResult {
      Objects.requireNonNull(snapshots, "snapshots is required");
      if (skipped < 0) {
        throw new IllegalArgumentException("skipped must be >= 0");
      }
    }
  }
}
