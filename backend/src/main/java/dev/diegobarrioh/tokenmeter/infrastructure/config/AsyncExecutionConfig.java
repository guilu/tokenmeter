package dev.diegobarrioh.tokenmeter.infrastructure.config;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalyzeThrottleProperties;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Wires the dedicated executor that runs {@code AnalysisJobExecutionService.runJob}. Activates both
 * {@code @Async} and {@code @Scheduled} processing for the application. {@link
 * dev.diegobarrioh.tokenmeter.infrastructure.pricing.PricingSchedulingConfig} also declares
 * {@code @EnableScheduling}, but it is opt-in via a feature flag; declaring it here ensures the
 * retention scheduler runs even when the pricing refresh feature is off. Spring deduplicates the
 * annotation across {@code @Configuration} classes.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncExecutionConfig {

  /**
   * Pool size and queue capacity are driven by {@link AnalyzeThrottleProperties}. After the
   * concurrent-analysis-limits change the executor exposes worker contention as visible queueing:
   * when every {@code corePoolSize == maxPoolSize == maxConcurrent} slot is busy, additional
   * submissions are enqueued in the internal {@link java.util.concurrent.LinkedBlockingQueue} of
   * capacity {@code queueCapacity} (default 256). {@code AbortPolicy} only fires when that queue
   * itself fills up — i.e. the executor cannot accept any more tasks — and that rejection bubbles
   * up as {@link java.util.concurrent.RejectedExecutionException}, which the submission service
   * maps to HTTP 429 with {@code error.code = RATE_LIMITED}. Plain worker-slot contention does NOT
   * produce a 429 any more.
   */
  @Bean(name = "analysisJobExecutor")
  public ThreadPoolTaskExecutor analysisJobExecutor(AnalyzeThrottleProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.maxConcurrent());
    executor.setMaxPoolSize(properties.maxConcurrent());
    executor.setQueueCapacity(properties.queueCapacity());
    executor.setThreadNamePrefix("tm-job-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
