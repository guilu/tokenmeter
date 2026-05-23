package dev.diegobarrioh.tokenmeter.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Pins the default queue capacity for the {@code analysisJobExecutor} bean. The bump from 32 to 256
 * introduced by the concurrent-analysis-limits change is transparent to wiring, so this test relies
 * on the property record's fallback (no {@code queue-capacity} property is overridden).
 */
@SpringBootTest
class AsyncExecutionConfigDefaultsTest {

  @Autowired
  @Qualifier("analysisJobExecutor")
  private ThreadPoolTaskExecutor executor;

  @Test
  void defaultQueueCapacityIs256() {
    assertThat(executor.getQueueCapacity()).isEqualTo(256);
    assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(256);
  }
}
