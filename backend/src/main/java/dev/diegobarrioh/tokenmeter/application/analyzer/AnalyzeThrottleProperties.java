package dev.diegobarrioh.tokenmeter.application.analyzer;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tokenmeter.analyze-throttle")
public record AnalyzeThrottleProperties(
    int maxConcurrent, int rateLimitRequestsPerWindow, Duration rateLimitWindowDuration) {
  public AnalyzeThrottleProperties {
    if (maxConcurrent <= 0) maxConcurrent = 3;
    if (rateLimitRequestsPerWindow <= 0) rateLimitRequestsPerWindow = 5;
    if (rateLimitWindowDuration == null
        || rateLimitWindowDuration.isNegative()
        || rateLimitWindowDuration.isZero()) {
      rateLimitWindowDuration = Duration.ofMinutes(1);
    }
  }
}
