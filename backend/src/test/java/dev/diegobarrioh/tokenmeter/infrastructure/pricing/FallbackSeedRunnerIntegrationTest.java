package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.pricing.JpaPricingSnapshotStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FallbackSeedRunnerIntegrationTest {

  @Autowired private FallbackSeedRunner seedRunner;
  @Autowired private JpaPricingSnapshotStore store;

  @Test
  void contextStartupSeedsSeventeenFallbackRowsAndSubsequentRunsAreNoOp() {
    long countAfterBoot = store.count();
    assertThat(countAfterBoot)
        .as("Spring Boot startup should seed exactly 17 baseline rows")
        .isEqualTo(17);

    List<PricingSnapshot> snapshots = store.findAll();
    assertThat(snapshots)
        .allSatisfy(snapshot -> assertThat(snapshot.source()).isEqualTo(PricingSource.FALLBACK));

    ApplicationArguments args = new DefaultApplicationArguments();
    seedRunner.run(args);
    seedRunner.run(args);

    assertThat(store.count())
        .as("Re-running the seed runner with a non-empty table must not change the row count")
        .isEqualTo(countAfterBoot);
  }
}
