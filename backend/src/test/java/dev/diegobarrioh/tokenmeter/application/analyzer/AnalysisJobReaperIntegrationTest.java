package dev.diegobarrioh.tokenmeter.application.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobErrorCode;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies that {@link AnalysisJobReaper} flips inherited non-terminal rows to {@code
 * FAILED/JOB_INTERRUPTED}.
 */
@SpringBootTest
class AnalysisJobReaperIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AnalysisJobReaper reaper;
  @Autowired private AnalysisJobRepository repository;

  @Test
  void runningRowsInheritedFromPreviousProcessAreMarkedInterrupted() {
    UUID jobId = UUID.randomUUID();
    Instant now = Instant.now();
    jdbcTemplate.update(
        "INSERT INTO analysis_job (id, repository_url, status, phase, progress_percent, "
            + "created_at, updated_at) VALUES (?, ?, 'RUNNING', 'CLONING_REPOSITORY', 15, ?, ?)",
        jobId,
        "https://github.com/foo/inherited",
        now,
        now);

    int reconciled = reaper.reconcile();

    assertThat(reconciled).isGreaterThanOrEqualTo(1);
    AnalysisJobSnapshot snapshot = repository.findById(new AnalysisJobId(jobId)).orElseThrow();
    assertThat(snapshot.status()).isEqualTo(AnalysisJobStatus.FAILED);
    assertThat(snapshot.errorCode()).isEqualTo(AnalysisJobErrorCode.JOB_INTERRUPTED);
    assertThat(snapshot.completedAt()).isNotNull();
  }
}
