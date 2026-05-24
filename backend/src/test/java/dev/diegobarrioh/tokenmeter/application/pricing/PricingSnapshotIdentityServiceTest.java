package dev.diegobarrioh.tokenmeter.application.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshedEvent;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotId;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PricingSnapshotIdentityServiceTest {

  private static final OffsetDateTime FETCHED_AT_A =
      OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime FETCHED_AT_B =
      OffsetDateTime.of(2026, 5, 24, 18, 42, 11, 0, ZoneOffset.UTC);

  @Test
  void identicalPricesYieldIdenticalIdsRegardlessOfFetchedAt() {
    List<PricingSnapshot> s1 =
        List.of(
            snapshot(AiProvider.OPENAI, "gpt-5", "5.000000", "15.000000", PricingSource.REMOTE,
                FETCHED_AT_A, "openai/gpt-5"),
            snapshot(AiProvider.ANTHROPIC, "claude-opus", "15.000000", "75.000000",
                PricingSource.REMOTE, FETCHED_AT_A, "anthropic/claude-opus"));
    List<PricingSnapshot> s2 =
        List.of(
            snapshot(AiProvider.OPENAI, "gpt-5", "5.000000", "15.000000", PricingSource.REMOTE,
                FETCHED_AT_B, "openai/gpt-5-v2"),
            snapshot(AiProvider.ANTHROPIC, "claude-opus", "15.000000", "75.000000",
                PricingSource.REMOTE, FETCHED_AT_B, null));

    PricingSnapshotId id1 = PricingSnapshotIdentityService.computeId(s1);
    PricingSnapshotId id2 = PricingSnapshotIdentityService.computeId(s2);

    assertThat(id1.value()).isEqualTo(id2.value());
  }

  @Test
  void aPriceChangeProducesADifferentId() {
    List<PricingSnapshot> base =
        List.of(snapshot(AiProvider.OPENAI, "gpt-5", "5.000000", "15.000000",
            PricingSource.REMOTE, FETCHED_AT_A, null));
    List<PricingSnapshot> bumped =
        List.of(snapshot(AiProvider.OPENAI, "gpt-5", "5.500000", "15.000000",
            PricingSource.REMOTE, FETCHED_AT_A, null));

    assertThat(PricingSnapshotIdentityService.computeId(base).value())
        .isNotEqualTo(PricingSnapshotIdentityService.computeId(bumped).value());
  }

  @Test
  void aSourceChangeProducesADifferentId() {
    List<PricingSnapshot> fallbackSourced =
        List.of(snapshot(AiProvider.OPENAI, "gpt-5", "5.000000", "15.000000",
            PricingSource.FALLBACK, FETCHED_AT_A, null));
    List<PricingSnapshot> remoteSourced =
        List.of(snapshot(AiProvider.OPENAI, "gpt-5", "5.000000", "15.000000",
            PricingSource.REMOTE, FETCHED_AT_A, null));

    assertThat(PricingSnapshotIdentityService.computeId(fallbackSourced).value())
        .isNotEqualTo(PricingSnapshotIdentityService.computeId(remoteSourced).value());
  }

  @Test
  void canonicalisationReSortsInputs() {
    List<PricingSnapshot> sorted =
        List.of(
            snapshot(AiProvider.ANTHROPIC, "claude-opus", "15.000000", "75.000000",
                PricingSource.REMOTE, FETCHED_AT_A, null),
            snapshot(AiProvider.OPENAI, "gpt-5", "5.000000", "15.000000", PricingSource.REMOTE,
                FETCHED_AT_A, null));
    List<PricingSnapshot> shuffled = new ArrayList<>(sorted);
    Collections.reverse(shuffled);

    assertThat(PricingSnapshotIdentityService.computeId(sorted).value())
        .isEqualTo(PricingSnapshotIdentityService.computeId(shuffled).value());
  }

  @Test
  void identifierCarriesV1PrefixAnd64HexSuffix() {
    PricingSnapshotId id =
        PricingSnapshotIdentityService.computeId(
            List.of(snapshot(AiProvider.OPENAI, "gpt-5", "5.000000", "15.000000",
                PricingSource.REMOTE, FETCHED_AT_A, null)));

    assertThat(id.value()).startsWith("v1:");
    assertThat(id.value()).hasSize(PricingSnapshotId.EXPECTED_LENGTH);
    assertThat(id.value().substring(3)).matches("[0-9a-f]{64}");
  }

  @Test
  void primarySourcePromotesOverrideOverRemoteAndFallback() {
    List<PricingSnapshot> mixed =
        List.of(
            snapshot(AiProvider.OPENAI, "gpt-5", "5.000000", "15.000000", PricingSource.FALLBACK,
                FETCHED_AT_A, null),
            snapshot(AiProvider.ANTHROPIC, "claude-opus", "15.000000", "75.000000",
                PricingSource.REMOTE, FETCHED_AT_A, null),
            snapshot(AiProvider.OPENAI, "internal", "1.000000", "2.000000", PricingSource.OVERRIDE,
                FETCHED_AT_A, null));

    assertThat(PricingSnapshotIdentityService.computePrimarySource(mixed))
        .isEqualTo(PricingSource.OVERRIDE);
  }

  @Test
  void primarySourceFallsBackToRemoteThenFallback() {
    assertThat(
            PricingSnapshotIdentityService.computePrimarySource(
                List.of(
                    snapshot(AiProvider.OPENAI, "gpt-5", "5.000000", "15.000000",
                        PricingSource.REMOTE, FETCHED_AT_A, null),
                    snapshot(AiProvider.ANTHROPIC, "claude-opus", "15.000000", "75.000000",
                        PricingSource.FALLBACK, FETCHED_AT_A, null))))
        .isEqualTo(PricingSource.REMOTE);

    assertThat(
            PricingSnapshotIdentityService.computePrimarySource(
                List.of(
                    snapshot(AiProvider.OPENAI, "gpt-5", "5.000000", "15.000000",
                        PricingSource.FALLBACK, FETCHED_AT_A, null))))
        .isEqualTo(PricingSource.FALLBACK);
  }

  @Test
  void captureIsStableAcrossReadsWithoutRefresh() {
    CountingProvider provider = new CountingProvider(samplePricings(PricingSource.REMOTE));
    PricingSnapshotIdentityService service =
        new PricingSnapshotIdentityService(provider, Clock.fixed(Instant.parse("2026-05-24T18:42:11Z"), ZoneOffset.UTC));

    PricingSnapshotHandle first = service.capture();
    PricingSnapshotHandle second = service.capture();

    assertThat(first.id().value()).isEqualTo(second.id().value());
    assertThat(first.capturedAt()).isEqualTo(second.capturedAt());
    assertThat(provider.calls.get()).isEqualTo(1);
  }

  @Test
  void refreshEventInvalidatesCache() {
    CountingProvider provider = new CountingProvider(samplePricings(PricingSource.FALLBACK));
    PricingSnapshotIdentityService service =
        new PricingSnapshotIdentityService(provider, Clock.systemUTC());

    PricingSnapshotHandle before = service.capture();
    provider.replace(samplePricings(PricingSource.REMOTE)); // simulate a refresh changing prices
    service.onPricingRefreshed(new PricingRefreshedEvent(FETCHED_AT_B, 1, 0));

    PricingSnapshotHandle after = service.capture();
    assertThat(after.id().value()).isNotEqualTo(before.id().value());
    assertThat(provider.calls.get()).isEqualTo(2);
  }

  private static List<PricingSnapshot> samplePricings(PricingSource source) {
    return List.of(
        snapshot(AiProvider.OPENAI, "gpt-5", "5.000000", "15.000000", source, FETCHED_AT_A, null),
        snapshot(AiProvider.ANTHROPIC, "claude-opus", "15.000000", "75.000000", source,
            FETCHED_AT_A, null));
  }

  private static PricingSnapshot snapshot(
      AiProvider provider,
      String model,
      String input,
      String output,
      PricingSource source,
      OffsetDateTime fetchedAt,
      String externalModelId) {
    ModelPricing pricing =
        new ModelPricing(provider, model, new BigDecimal(input), new BigDecimal(output));
    return new PricingSnapshot(pricing, source, fetchedAt, externalModelId);
  }

  private static final class CountingProvider implements PricingProvider {

    private final AtomicInteger calls = new AtomicInteger();
    private volatile List<PricingSnapshot> snapshots;

    CountingProvider(List<PricingSnapshot> snapshots) {
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
      calls.incrementAndGet();
      return snapshots;
    }
  }
}
