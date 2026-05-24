package dev.diegobarrioh.tokenmeter.application.cost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingSnapshotIdentityService;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryCostEstimationServiceHandleTest {

  @Test
  void handleOverloadIgnoresProviderMutationsMidCall() {
    MutableProvider provider = new MutableProvider(samplePricings("2.50", "10.00"));
    RepositoryCostEstimationService service = new RepositoryCostEstimationService(provider);
    PricingSnapshotHandle handle = handleFor(samplePricings("2.50", "10.00"));

    // Mutate the provider AFTER the handle was captured; the call must ignore this.
    provider.replace(samplePricings("99.00", "99.00"));

    List<ModelCostEstimate> withHandle = service.estimate(1_000_000L, handle);

    assertThat(withHandle).isNotEmpty();
    // input cost = 0 (RAW mode), output cost = (1_000_000 * 10.00) / 1_000_000 = 10.000000
    assertThat(withHandle.get(0).totalCost()).isEqualByComparingTo("10.000000");
  }

  @Test
  void rejectsNullHandle() {
    RepositoryCostEstimationService service =
        new RepositoryCostEstimationService(new MutableProvider(samplePricings("1", "1")));

    assertThatThrownBy(() -> service.estimate(0L, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("handle");
  }

  @Test
  void rejectsNegativeBaseTokensInHandleOverload() {
    RepositoryCostEstimationService service =
        new RepositoryCostEstimationService(new MutableProvider(samplePricings("1", "1")));

    assertThatThrownBy(() -> service.estimate(-1L, handleFor(samplePricings("1", "1"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static List<PricingSnapshot> samplePricings(String input, String output) {
    return List.of(
        new PricingSnapshot(
            new ModelPricing(
                AiProvider.OPENAI, "gpt-4o", new BigDecimal(input), new BigDecimal(output)),
            PricingSource.REMOTE,
            OffsetDateTime.of(2026, 5, 24, 0, 0, 0, 0, ZoneOffset.UTC),
            null));
  }

  private static PricingSnapshotHandle handleFor(List<PricingSnapshot> snapshots) {
    return new PricingSnapshotHandle(
        PricingSnapshotIdentityService.computeId(snapshots),
        PricingSnapshotIdentityService.computePrimarySource(snapshots),
        Instant.parse("2026-05-24T18:42:11Z"),
        snapshots);
  }

  private static final class MutableProvider implements PricingProvider {

    private volatile List<PricingSnapshot> snapshots;

    MutableProvider(List<PricingSnapshot> snapshots) {
      this.snapshots = snapshots;
    }

    void replace(List<PricingSnapshot> next) {
      this.snapshots = next;
    }

    @Override
    public List<ModelPricing> all() {
      return snapshots.stream().map(PricingSnapshot::pricing).toList();
    }

    @Override
    public Optional<ModelPricing> find(AiProvider provider, String model) {
      return Optional.empty();
    }

    @Override
    public List<PricingSnapshot> snapshots() {
      return snapshots;
    }
  }
}
