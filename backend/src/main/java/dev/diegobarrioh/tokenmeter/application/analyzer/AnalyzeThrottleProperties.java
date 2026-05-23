package dev.diegobarrioh.tokenmeter.application.analyzer;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tokenmeter.analyze-throttle")
public record AnalyzeThrottleProperties(
    int maxConcurrent,
    int queueCapacity,
    int rateLimitRequestsPerWindow,
    Duration rateLimitWindowDuration,
    Retention retention) {

  public AnalyzeThrottleProperties {
    if (maxConcurrent <= 0) {
      maxConcurrent = 3;
    }
    if (queueCapacity <= 0) {
      queueCapacity = 32;
    }
    if (rateLimitRequestsPerWindow <= 0) {
      rateLimitRequestsPerWindow = 5;
    }
    if (rateLimitWindowDuration == null
        || rateLimitWindowDuration.isNegative()
        || rateLimitWindowDuration.isZero()) {
      rateLimitWindowDuration = Duration.ofMinutes(1);
    }
    if (retention == null) {
      retention = Retention.defaults();
    }
  }

  /** Retention configuration for terminal jobs. */
  public record Retention(Duration success, Duration failed, String cron) {
    public Retention {
      if (success == null || success.isNegative() || success.isZero()) {
        success = Duration.ofDays(7);
      }
      if (failed == null || failed.isNegative() || failed.isZero()) {
        failed = Duration.ofDays(30);
      }
      if (cron == null || cron.isBlank()) {
        cron = "0 30 3 * * *";
      }
    }

    public static Retention defaults() {
      return new Retention(Duration.ofDays(7), Duration.ofDays(30), "0 30 3 * * *");
    }
  }
}
