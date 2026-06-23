package dev.diegobarrioh.tokenmeter.application.cost;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingSnapshotIdentityService;
import dev.diegobarrioh.tokenmeter.application.tokenizer.ModelTokenizationProfileResolver;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenizationPrecision;
import dev.diegobarrioh.tokenmeter.infrastructure.tokenizer.TokenizerProfileLoader;
import dev.diegobarrioh.tokenmeter.infrastructure.tokenizer.TokenizerProfileProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests for the new {@code estimate(Map<String,Long>, PricingSnapshotHandle)} overload introduced
 * in Slice B. Each model uses its tokenizer's count; back-compat overloads remain unaffected.
 */
class RepositoryCostEstimationServiceMapOverloadTest {

  private static final String O200K_ID = "openai/o200k_base";
  private static final String HEURISTIC_ID = "anthropic/cl100k_heuristic";

  // --- Scenario: OpenAI model uses EXACT_LOCAL count ---

  @Test
  void openAiModelUsesO200kTokenizerCount() {
    RepositoryCostEstimationService service =
        serviceWith(
            new ModelPricing(
                AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")));

    Map<String, Long> tokenMap = Map.of(O200K_ID, 5000L, HEURISTIC_ID, 4750L);
    PricingSnapshotHandle handle =
        handleFor(List.of(snapshot(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00")));

    List<ModelCostEstimate> estimates = service.estimate(tokenMap, handle);

    // 3 modes per model → 3 estimates
    assertThat(estimates).hasSize(3);
    estimates.forEach(
        e -> {
          assertThat(e.baseTokens()).isEqualTo(5000L);
          assertThat(e.tokenizerId()).isEqualTo(O200K_ID);
          assertThat(e.precision()).isEqualTo(TokenizationPrecision.EXACT_LOCAL);
        });
  }

  // --- Scenario: Anthropic model uses HEURISTIC count ---

  @Test
  void anthropicModelUsesHeuristicTokenizerCount() {
    RepositoryCostEstimationService service =
        serviceWith(
            new ModelPricing(
                AiProvider.ANTHROPIC,
                "claude-3-5-sonnet-20241022",
                new BigDecimal("3.00"),
                new BigDecimal("15.00")));

    Map<String, Long> tokenMap = Map.of(O200K_ID, 5000L, HEURISTIC_ID, 4750L);
    PricingSnapshotHandle handle =
        handleFor(
            List.of(snapshot(AiProvider.ANTHROPIC, "claude-3-5-sonnet-20241022", "3.00", "15.00")));

    List<ModelCostEstimate> estimates = service.estimate(tokenMap, handle);

    assertThat(estimates).hasSize(3);
    estimates.forEach(
        e -> {
          assertThat(e.baseTokens()).isEqualTo(4750L);
          assertThat(e.tokenizerId()).isEqualTo(HEURISTIC_ID);
          assertThat(e.precision()).isEqualTo(TokenizationPrecision.HEURISTIC);
        });
  }

  // --- Scenario: Missing tokenizerId in map → base 0, no exception ---

  @Test
  void unknownTokenizerIdInMapDefaultsToZeroBase() {
    RepositoryCostEstimationService service =
        serviceWith(
            new ModelPricing(
                AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")));

    // Map is empty — tokenizerId not present → base should be 0
    Map<String, Long> emptyMap = Map.of();
    PricingSnapshotHandle handle =
        handleFor(List.of(snapshot(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00")));

    List<ModelCostEstimate> estimates = service.estimate(emptyMap, handle);

    assertThat(estimates).hasSize(3);
    estimates.forEach(e -> assertThat(e.baseTokens()).isEqualTo(0L));
  }

  // --- Scenario: Back-compat estimate(long) still carries o200k/EXACT_LOCAL ---

  @Test
  void backCompatSingleLongOverloadCarriesO200kAndExactLocal() {
    RepositoryCostEstimationService service =
        serviceWith(
            new ModelPricing(
                AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")));

    List<ModelCostEstimate> estimates = service.estimate(1000L);

    assertThat(estimates).isNotEmpty();
    estimates.forEach(
        e -> {
          assertThat(e.tokenizerId()).isEqualTo(O200K_ID);
          assertThat(e.precision()).isEqualTo(TokenizationPrecision.EXACT_LOCAL);
        });
  }

  // --- Scenario: Back-compat estimate(long, handle) still carries o200k/EXACT_LOCAL ---

  @Test
  void backCompatHandleLongOverloadCarriesO200kAndExactLocal() {
    RepositoryCostEstimationService service =
        serviceWith(
            new ModelPricing(
                AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")));

    List<PricingSnapshot> snapshots =
        List.of(snapshot(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00"));
    PricingSnapshotHandle handle = handleFor(snapshots);

    List<ModelCostEstimate> estimates = service.estimate(1000L, handle);

    assertThat(estimates).isNotEmpty();
    estimates.forEach(
        e -> {
          assertThat(e.tokenizerId()).isEqualTo(O200K_ID);
          assertThat(e.precision()).isEqualTo(TokenizationPrecision.EXACT_LOCAL);
        });
  }

  // --- ModelCostEstimate carries tokenizerId and precision (non-null for new estimates) ---

  @Test
  void newMapOverloadEstimatesHaveNonNullTokenizerFields() {
    RepositoryCostEstimationService service =
        serviceWith(
            new ModelPricing(
                AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")));

    Map<String, Long> tokenMap = Map.of(O200K_ID, 100L);
    PricingSnapshotHandle handle =
        handleFor(List.of(snapshot(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00")));

    List<ModelCostEstimate> estimates = service.estimate(tokenMap, handle);

    estimates.forEach(
        e -> {
          assertThat(e.tokenizerId()).isNotNull();
          assertThat(e.precision()).isNotNull();
        });
  }

  // --- Multi-model: each uses its own tokenizer count ---

  @Test
  void mixedProviderSnapshotEachModelUsesItsTokenizerCount() {
    RepositoryCostEstimationService service =
        serviceWithList(
            List.of(
                new ModelPricing(
                    AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")),
                new ModelPricing(
                    AiProvider.ANTHROPIC,
                    "claude-3-5-sonnet-20241022",
                    new BigDecimal("3.00"),
                    new BigDecimal("15.00"))));

    Map<String, Long> tokenMap = Map.of(O200K_ID, 5000L, HEURISTIC_ID, 4750L);
    List<PricingSnapshot> snapshots =
        List.of(
            snapshot(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00"),
            snapshot(AiProvider.ANTHROPIC, "claude-3-5-sonnet-20241022", "3.00", "15.00"));
    PricingSnapshotHandle handle = handleFor(snapshots);

    List<ModelCostEstimate> estimates = service.estimate(tokenMap, handle);

    // 2 models × 3 modes = 6 estimates
    assertThat(estimates).hasSize(6);
    List<ModelCostEstimate> openAiEstimates =
        estimates.stream().filter(e -> e.provider() == AiProvider.OPENAI).toList();
    List<ModelCostEstimate> anthropicEstimates =
        estimates.stream().filter(e -> e.provider() == AiProvider.ANTHROPIC).toList();

    openAiEstimates.forEach(
        e -> {
          assertThat(e.baseTokens()).isEqualTo(5000L);
          assertThat(e.tokenizerId()).isEqualTo(O200K_ID);
          assertThat(e.precision()).isEqualTo(TokenizationPrecision.EXACT_LOCAL);
        });
    anthropicEstimates.forEach(
        e -> {
          assertThat(e.baseTokens()).isEqualTo(4750L);
          assertThat(e.tokenizerId()).isEqualTo(HEURISTIC_ID);
          assertThat(e.precision()).isEqualTo(TokenizationPrecision.HEURISTIC);
        });
  }

  // --- Canonical ordering preserved in new overload ---

  @Test
  void mapOverloadPreservesCanonicalOrderByProviderThenModel() {
    RepositoryCostEstimationService service =
        serviceWithList(
            List.of(
                new ModelPricing(
                    AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")),
                new ModelPricing(
                    AiProvider.ANTHROPIC,
                    "claude-3-5-sonnet-20241022",
                    new BigDecimal("3.00"),
                    new BigDecimal("15.00"))));

    Map<String, Long> tokenMap = Map.of(O200K_ID, 5000L, HEURISTIC_ID, 4750L);
    List<PricingSnapshot> snapshots =
        List.of(
            snapshot(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00"),
            snapshot(AiProvider.ANTHROPIC, "claude-3-5-sonnet-20241022", "3.00", "15.00"));
    PricingSnapshotHandle handle = handleFor(snapshots);

    List<ModelCostEstimate> estimates = service.estimate(tokenMap, handle);

    // Anthropic comes before OpenAI alphabetically
    assertThat(estimates.get(0).provider()).isEqualTo(AiProvider.ANTHROPIC);
    assertThat(estimates.get(3).provider()).isEqualTo(AiProvider.OPENAI);
    // All three modes per provider
    assertThat(estimates.get(0).mode()).isEqualTo(CostEstimationMode.RAW);
    assertThat(estimates.get(1).mode()).isEqualTo(CostEstimationMode.ASSISTED);
    assertThat(estimates.get(2).mode()).isEqualTo(CostEstimationMode.AGENTIC);
  }

  // --- Helpers ---

  private static RepositoryCostEstimationService serviceWith(ModelPricing pricing) {
    return serviceWithList(List.of(pricing));
  }

  private static RepositoryCostEstimationService serviceWithList(List<ModelPricing> pricings) {
    PricingProvider provider =
        new PricingProvider() {
          @Override
          public List<ModelPricing> all() {
            return pricings;
          }

          @Override
          public Optional<ModelPricing> find(AiProvider p, String model) {
            return Optional.empty();
          }
        };

    // Build the full resolver chain using production YAML
    org.springframework.core.io.DefaultResourceLoader resourceLoader =
        new org.springframework.core.io.DefaultResourceLoader();
    TokenizerProfileLoader loader =
        new TokenizerProfileLoader(resourceLoader, new TokenizerProfileProperties(null));
    ModelTokenizationProfileResolver resolver = new ModelTokenizationProfileResolver(loader);

    return new RepositoryCostEstimationService(provider, resolver);
  }

  private static PricingSnapshot snapshot(
      AiProvider provider, String model, String inputPrice, String outputPrice) {
    return new PricingSnapshot(
        new ModelPricing(provider, model, new BigDecimal(inputPrice), new BigDecimal(outputPrice)),
        PricingSource.REMOTE,
        OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC),
        null);
  }

  private static PricingSnapshotHandle handleFor(List<PricingSnapshot> snapshots) {
    return new PricingSnapshotHandle(
        PricingSnapshotIdentityService.computeId(snapshots),
        PricingSnapshotIdentityService.computePrimarySource(snapshots),
        Instant.parse("2026-06-22T10:00:00Z"),
        snapshots);
  }
}
