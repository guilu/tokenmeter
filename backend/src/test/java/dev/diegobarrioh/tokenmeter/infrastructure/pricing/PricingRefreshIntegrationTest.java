package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingFetchPort;
import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshException;
import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshService;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.pricing.JpaPricingSnapshotStore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@org.springframework.context.annotation.Import(
    PricingRefreshIntegrationTest.StubPricingFetchConfig.class)
class PricingRefreshIntegrationTest {

  @Autowired private PricingRefreshService refreshService;
  @Autowired private JpaPricingSnapshotStore store;
  @Autowired private StubPricingFetcher stubFetcher;

  @Test
  @Transactional
  void successfulRefreshUpgradesFallbackRowsToRemoteForMappedEntries() {
    stubFetcher.setFailing(false);
    long beforeCount = store.count();
    assertThat(beforeCount).isEqualTo(17);

    refreshService.refresh();

    List<PricingSnapshot> after = store.findAll();
    assertThat(after).isNotEmpty();
    PricingSnapshot gpt4o =
        after.stream()
            .filter(s -> s.provider() == AiProvider.OPENAI && s.model().equals("gpt-4o"))
            .findFirst()
            .orElseThrow();
    assertThat(gpt4o.source()).isEqualTo(PricingSource.REMOTE);
    assertThat(gpt4o.pricing().inputTokenPricePerMillion()).isEqualByComparingTo("4.000000");
    assertThat(gpt4o.pricing().outputTokenPricePerMillion()).isEqualByComparingTo("16.000000");
    assertThat(gpt4o.externalModelId()).isEqualTo("gpt-4o");
  }

  @Test
  void failingRefreshLeavesExistingRowsUntouched() {
    long beforeCount = store.count();
    List<PricingSnapshot> beforeSnapshot = store.findAll();
    stubFetcher.setFailing(true);

    assertThatThrownBy(() -> refreshService.refresh()).isInstanceOf(PricingRefreshException.class);

    assertThat(store.count()).isEqualTo(beforeCount);
    assertThat(store.findAll())
        .extracting(s -> s.provider().configKey() + ":" + s.model() + ":" + s.source())
        .containsExactlyInAnyOrderElementsOf(
            beforeSnapshot.stream()
                .map(s -> s.provider().configKey() + ":" + s.model() + ":" + s.source())
                .toList());
  }

  @TestConfiguration
  static class StubPricingFetchConfig {

    @Bean
    @Primary
    StubPricingFetcher stubPricingFetcher() {
      return new StubPricingFetcher();
    }
  }

  static class StubPricingFetcher implements PricingFetchPort {

    private final AtomicBoolean failing = new AtomicBoolean(false);

    void setFailing(boolean fail) {
      failing.set(fail);
    }

    @Override
    public List<PricingSnapshot> fetchAndMap(OffsetDateTime fetchedAt) {
      if (failing.get()) {
        throw new PricingRefreshException("simulated upstream failure");
      }
      return List.of(
          new PricingSnapshot(
              new ModelPricing(
                  AiProvider.OPENAI, "gpt-4o", new BigDecimal("4.00"), new BigDecimal("16.00")),
              PricingSource.REMOTE,
              fetchedAt,
              "gpt-4o"),
          new PricingSnapshot(
              new ModelPricing(
                  AiProvider.ANTHROPIC,
                  "claude-opus-4-7",
                  new BigDecimal("20.00"),
                  new BigDecimal("100.00")),
              PricingSource.REMOTE,
              fetchedAt,
              "claude-opus-4-7"));
    }

    @Override
    public int configuredMappingCount() {
      return 17;
    }
  }
}
