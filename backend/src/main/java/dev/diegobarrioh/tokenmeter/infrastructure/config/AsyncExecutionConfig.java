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
   * Pool size and queue capacity are driven by {@link AnalyzeThrottleProperties}. {@code
   * AbortPolicy} causes saturated submissions to surface as {@link
   * java.util.concurrent.RejectedExecutionException}, which the submission service maps to HTTP
   * 429.
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
