package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic job that removes terminal {@code AnalysisJob} rows once their retention window has
 * elapsed. Defaults: 7 days for SUCCESS, 30 days for FAILED. Configurable through {@link
 * AnalyzeThrottleProperties.Retention}.
 */
@Component
public class AnalysisJobRetentionScheduler {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisJobRetentionScheduler.class);

  private final AnalysisJobRepository jobRepository;
  private final AnalyzeThrottleProperties properties;
  private final Clock clock;

  public AnalysisJobRetentionScheduler(
      AnalysisJobRepository jobRepository, AnalyzeThrottleProperties properties, Clock clock) {
    this.jobRepository = jobRepository;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(cron = "${tokenmeter.analyze-throttle.retention.cron:0 30 3 * * *}")
  public void purgeExpiredJobs() {
    runOnce();
  }

  /** Visible for tests — executes a single retention pass with the current clock. */
  public RetentionReport runOnce() {
    Instant now = Instant.now(clock);
    AnalyzeThrottleProperties.Retention retention = properties.retention();
    int successRemoved =
        jobRepository.deleteCompletedBefore(
            AnalysisJobStatus.SUCCESS, now.minus(retention.success()));
    int failedRemoved =
        jobRepository.deleteCompletedBefore(
            AnalysisJobStatus.FAILED, now.minus(retention.failed()));
    if (successRemoved > 0 || failedRemoved > 0) {
      LOGGER.info(
          "Retention sweep removed {} SUCCESS and {} FAILED rows", successRemoved, failedRemoved);
    }
    return new RetentionReport(successRemoved, failedRemoved);
  }

  public record RetentionReport(int successRemoved, int failedRemoved) {}
}
