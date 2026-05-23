package dev.diegobarrioh.tokenmeter.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Verifies the {@code analysisJobExecutor} bean honours configuration properties. */
@SpringBootTest(
    properties = {
      "tokenmeter.analyze-throttle.max-concurrent=4",
      "tokenmeter.analyze-throttle.queue-capacity=17"
    })
class AsyncExecutionConfigTest {

  @Autowired
  @Qualifier("analysisJobExecutor")
  private ThreadPoolTaskExecutor executor;

  @Test
  void exposesThreadPoolWithConfiguredSizes() {
    assertThat(executor).isNotNull();
    assertThat(executor.getCorePoolSize()).isEqualTo(4);
    assertThat(executor.getMaxPoolSize()).isEqualTo(4);
    assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(17);
    assertThat(executor.getThreadNamePrefix()).startsWith("tm-job-");
  }
}
