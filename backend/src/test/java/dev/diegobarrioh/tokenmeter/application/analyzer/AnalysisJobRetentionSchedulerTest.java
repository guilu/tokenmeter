package dev.diegobarrioh.tokenmeter.application.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AnalysisJobRetentionSchedulerTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-05-23T09:00:00Z");

  @Test
  void deletesUsingDefaultRetentionWindows() {
    AnalysisJobRepository repository = Mockito.mock(AnalysisJobRepository.class);
    AnalyzeThrottleProperties properties =
        new AnalyzeThrottleProperties(3, 32, 5, Duration.ofMinutes(1), null);
    Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    when(repository.deleteCompletedBefore(
            eq(AnalysisJobStatus.SUCCESS), eq(FIXED_NOW.minus(Duration.ofDays(7)))))
        .thenReturn(2);
    when(repository.deleteCompletedBefore(
            eq(AnalysisJobStatus.FAILED), eq(FIXED_NOW.minus(Duration.ofDays(30)))))
        .thenReturn(5);

    AnalysisJobRetentionScheduler scheduler =
        new AnalysisJobRetentionScheduler(repository, properties, clock);

    AnalysisJobRetentionScheduler.RetentionReport report = scheduler.runOnce();

    assertThat(report.successRemoved()).isEqualTo(2);
    assertThat(report.failedRemoved()).isEqualTo(5);
    verify(repository)
        .deleteCompletedBefore(AnalysisJobStatus.SUCCESS, FIXED_NOW.minus(Duration.ofDays(7)));
    verify(repository)
        .deleteCompletedBefore(AnalysisJobStatus.FAILED, FIXED_NOW.minus(Duration.ofDays(30)));
  }

  @Test
  void honoursOverriddenRetentionWindows() {
    AnalysisJobRepository repository = Mockito.mock(AnalysisJobRepository.class);
    AnalyzeThrottleProperties properties =
        new AnalyzeThrottleProperties(
            3,
            32,
            5,
            Duration.ofMinutes(1),
            new AnalyzeThrottleProperties.Retention(
                Duration.ofHours(6), Duration.ofDays(2), "0 0 * * * *"));
    Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    when(repository.deleteCompletedBefore(
            eq(AnalysisJobStatus.SUCCESS), eq(FIXED_NOW.minus(Duration.ofHours(6)))))
        .thenReturn(1);
    when(repository.deleteCompletedBefore(
            eq(AnalysisJobStatus.FAILED), eq(FIXED_NOW.minus(Duration.ofDays(2)))))
        .thenReturn(3);

    AnalysisJobRetentionScheduler scheduler =
        new AnalysisJobRetentionScheduler(repository, properties, clock);

    AnalysisJobRetentionScheduler.RetentionReport report = scheduler.runOnce();

    assertThat(report.successRemoved()).isEqualTo(1);
    assertThat(report.failedRemoved()).isEqualTo(3);
  }
}
