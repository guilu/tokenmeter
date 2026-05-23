package dev.diegobarrioh.tokenmeter.application.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** End-to-end retention sweep with a fixed clock. */
@SpringBootTest
@Import(AnalysisJobRetentionSchedulerIntegrationTest.FixedClockConfig.class)
class AnalysisJobRetentionSchedulerIntegrationTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-05-23T09:00:00Z");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AnalysisJobRetentionScheduler scheduler;
  @Autowired private AnalysisJobRepository repository;

  @Test
  void purgesOnlyJobsWhoseCompletionExceedsRetention() {
    UUID freshSuccess = UUID.randomUUID();
    UUID oldSuccess = UUID.randomUUID();
    UUID freshFailed = UUID.randomUUID();
    UUID oldFailed = UUID.randomUUID();

    insertTerminalJob(freshSuccess, "SUCCESS", FIXED_NOW.minusSeconds(60));
    insertTerminalJob(oldSuccess, "SUCCESS", FIXED_NOW.minusSeconds(86400L * 8));
    insertTerminalJob(freshFailed, "FAILED", FIXED_NOW.minusSeconds(86400L * 2));
    insertTerminalJob(oldFailed, "FAILED", FIXED_NOW.minusSeconds(86400L * 31));

    AnalysisJobRetentionScheduler.RetentionReport report = scheduler.runOnce();

    assertThat(report.successRemoved()).isEqualTo(1);
    assertThat(report.failedRemoved()).isEqualTo(1);
    assertThat(repository.findById(new AnalysisJobId(freshSuccess))).isPresent();
    assertThat(repository.findById(new AnalysisJobId(oldSuccess))).isEmpty();
    assertThat(repository.findById(new AnalysisJobId(freshFailed))).isPresent();
    assertThat(repository.findById(new AnalysisJobId(oldFailed))).isEmpty();
  }

  private void insertTerminalJob(UUID id, String status, Instant completedAt) {
    UUID analysisId = null;
    short progress = (short) 0;
    String errorCode = null;
    String errorMessage = null;
    if ("SUCCESS".equals(status)) {
      analysisId = UUID.randomUUID();
      insertAnalysisRowFor(analysisId);
      progress = (short) 100;
    } else {
      errorCode = "ANALYSIS_FAILED";
      errorMessage = "test";
    }
    jdbcTemplate.update(
        "INSERT INTO analysis_job (id, repository_url, status, phase, progress_percent, "
            + "analysis_id, error_code, error_message, created_at, updated_at, completed_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        "https://github.com/foo/" + status.toLowerCase(),
        status,
        "SUCCESS".equals(status) ? "COMPLETED" : "FAILED",
        progress,
        analysisId,
        errorCode,
        errorMessage,
        completedAt,
        completedAt,
        completedAt);
  }

  private void insertAnalysisRowFor(UUID analysisId) {
    jdbcTemplate.update(
        "INSERT INTO analysis (id, repository_url, clone_url, owner_name, repository_name, "
            + "status, total_files, total_lines, total_bytes, token_encoding, total_tokens, "
            + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
        analysisId,
        "https://github.com/foo/dummy",
        "https://github.com/foo/dummy.git",
        "foo",
        "dummy",
        "SUCCESS",
        1L,
        1L,
        1L,
        "o200k_base",
        1L);
  }

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(FIXED_NOW, java.time.ZoneOffset.UTC);
    }
  }
}
