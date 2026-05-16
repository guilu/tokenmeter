package dev.diegobarrioh.tokenmeter.infrastructure.persistence.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaPricingSnapshotStore.class)
class JpaPricingSnapshotStoreTest {

  private static final OffsetDateTime REMOTE_AT =
      OffsetDateTime.of(2026, 5, 15, 3, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime FALLBACK_AT =
      OffsetDateTime.of(2026, 5, 14, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime OVERRIDE_AT =
      OffsetDateTime.of(2026, 5, 15, 5, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private JpaPricingSnapshotStore store;
  @Autowired private ModelPricingJpaRepository repository;

  @Test
  void replaceAllPersistsEveryProvidedSnapshotAndClearsExistingRows() {
    store.replaceAll(
        List.of(
            snapshot(
                AiProvider.OPENAI, "gpt-4o", PricingSource.FALLBACK, FALLBACK_AT, "2.50", "10.00"),
            snapshot(
                AiProvider.ANTHROPIC,
                "claude-opus-4-7",
                PricingSource.FALLBACK,
                FALLBACK_AT,
                "15.00",
                "75.00")));

    assertThat(store.count()).isEqualTo(2);
    assertThat(store.findAll())
        .extracting(s -> s.provider().configKey() + ":" + s.model())
        .containsExactlyInAnyOrder("openai:gpt-4o", "anthropic:claude-opus-4-7");

    store.replaceAll(
        List.of(
            snapshot(
                AiProvider.OPENAI,
                "gpt-4o",
                PricingSource.FALLBACK,
                FALLBACK_AT,
                "2.50",
                "10.00")));

    assertThat(store.count()).isEqualTo(1);
  }

  @Test
  void replaceRemoteDeletesRemoteAndFallbackButNotOverrideRows() {
    seedManually(
        AiProvider.OPENAI, "gpt-4o", PricingSource.REMOTE, REMOTE_AT, "2.50", "10.00", "gpt-4o");
    seedManually(
        AiProvider.ANTHROPIC,
        "claude-opus-4-7",
        PricingSource.FALLBACK,
        FALLBACK_AT,
        "15.00",
        "75.00",
        null);
    seedManually(
        AiProvider.DEEPSEEK,
        "deepseek-chat",
        PricingSource.OVERRIDE,
        OVERRIDE_AT,
        "0.20",
        "0.80",
        null);

    store.replaceRemote(
        List.of(
            snapshot(AiProvider.OPENAI, "gpt-4o", PricingSource.REMOTE, REMOTE_AT, "3.00", "12.00"),
            snapshot(
                AiProvider.MISTRAL, "codestral", PricingSource.REMOTE, REMOTE_AT, "0.20", "0.60")));

    List<PricingSnapshot> persisted = store.findAll();
    assertThat(persisted)
        .extracting(s -> s.provider().configKey() + ":" + s.model() + ":" + s.source())
        .containsExactlyInAnyOrder(
            "openai:gpt-4o:REMOTE", "mistral:codestral:REMOTE", "deepseek:deepseek-chat:OVERRIDE");

    PricingSnapshot updatedGpt4o =
        persisted.stream()
            .filter(s -> s.provider() == AiProvider.OPENAI && s.model().equals("gpt-4o"))
            .findFirst()
            .orElseThrow();
    assertThat(updatedGpt4o.pricing().inputTokenPricePerMillion()).isEqualByComparingTo("3.000000");
    assertThat(updatedGpt4o.pricing().outputTokenPricePerMillion())
        .isEqualByComparingTo("12.000000");
    assertThat(updatedGpt4o.source()).isEqualTo(PricingSource.REMOTE);
    assertThat(updatedGpt4o.fetchedAt()).isEqualTo(REMOTE_AT);
  }

  @Test
  void duplicateSnapshotsViolateUniqueConstraintAndKeepTableUnchanged() {
    seedManually(
        AiProvider.OPENAI, "gpt-4o", PricingSource.FALLBACK, FALLBACK_AT, "2.50", "10.00", null);
    assertThat(store.count()).isEqualTo(1);

    PricingSnapshot duplicateA =
        snapshot(AiProvider.MISTRAL, "codestral", PricingSource.REMOTE, REMOTE_AT, "0.20", "0.60");
    PricingSnapshot duplicateB =
        snapshot(AiProvider.MISTRAL, "codestral", PricingSource.REMOTE, REMOTE_AT, "0.99", "1.99");

    assertThatThrownBy(() -> store.replaceRemote(List.of(duplicateA, duplicateB)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void seedManually(
      AiProvider provider,
      String model,
      PricingSource source,
      OffsetDateTime fetchedAt,
      String input,
      String output,
      String externalModelId) {
    ModelPricingEntity entity =
        new ModelPricingEntity(
            provider.configKey(),
            model,
            new BigDecimal(input).setScale(6),
            new BigDecimal(output).setScale(6),
            source,
            fetchedAt,
            externalModelId,
            null);
    repository.saveAndFlush(entity);
  }

  private static PricingSnapshot snapshot(
      AiProvider provider,
      String model,
      PricingSource source,
      OffsetDateTime fetchedAt,
      String input,
      String output) {
    return new PricingSnapshot(
        new ModelPricing(provider, model, new BigDecimal(input), new BigDecimal(output)),
        source,
        fetchedAt,
        source == PricingSource.REMOTE ? model : null);
  }
}
