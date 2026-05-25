package dev.diegobarrioh.tokenmeter.application.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import dev.diegobarrioh.tokenmeter.application.repository.GitRepositoryCloner;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobErrorCode;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class AnalysisJobExecutionServicePricingIntegrationTest {

  @Autowired private AnalysisJobExecutionService executionService;
  @Autowired private AnalysisJobRepository jobRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private GitRepositoryCloner cloner;

  @Test
  void successfulJobPersistsSamePricingSnapshotOnJobAndAnalysis() {
    doAnswer(
            invocation -> {
              writeFile(invocation.getArgument(1), "src/App.java", "class App {}\n");
              return null;
            })
        .when(cloner)
        .clone(any(), any(), any());
    AnalysisJobId jobId = saveQueuedJob("https://github.com/guilu/tokenmeter");

    executionService.runJobInternal(jobId);

    AnalysisJobSnapshot job = jobRepository.findById(jobId).orElseThrow();
    assertThat(job.status()).isEqualTo(AnalysisJobStatus.SUCCESS);
    assertThat(job.analysisId()).isNotNull();
    assertThat(job.pricing()).isNotNull();

    String analysisPricingId =
        jdbcTemplate.queryForObject(
            "SELECT pricing_snapshot_id FROM analysis WHERE id = ?",
            String.class,
            job.analysisId());

    assertThat(analysisPricingId).isEqualTo(job.pricing().snapshotId().value());
    assertThat(job.pricing().primarySource()).isNotNull();
    assertThat(job.pricing().capturedAt()).isNotNull();
  }

  @Test
  void preCostFailureLeavesPricingSnapshotNullOnJob() {
    doAnswer(
            invocation -> {
              throw new RepositoryIntakeException(
                  RepositoryIntakeErrorCode.CLONE_TIMEOUT, "clone timed out");
            })
        .when(cloner)
        .clone(any(), any(), any());
    AnalysisJobId jobId = saveQueuedJob("https://github.com/guilu/slow");

    executionService.runJobInternal(jobId);

    AnalysisJobSnapshot job = jobRepository.findById(jobId).orElseThrow();
    assertThat(job.status()).isEqualTo(AnalysisJobStatus.FAILED);
    assertThat(job.errorCode()).isEqualTo(AnalysisJobErrorCode.CLONE_TIMEOUT);
    assertThat(job.pricing()).isNull();

    String persistedPricingId =
        jdbcTemplate.queryForObject(
            "SELECT pricing_snapshot_id FROM analysis_job WHERE id = ?",
            String.class,
            jobId.value());
    assertThat(persistedPricingId).isNull();
  }

  private AnalysisJobId saveQueuedJob(String repositoryUrl) {
    AnalysisJobId id = AnalysisJobId.random();
    Instant now = Instant.now();
    jobRepository.save(
        new AnalysisJobSnapshot(
            id,
            repositoryUrl,
            AnalysisJobStatus.QUEUED,
            AnalysisJobPhase.QUEUED,
            0,
            "Job queued",
            null,
            null,
            null,
            AnalysisJobMetrics.empty(),
            now,
            null,
            now,
            null));
    return id;
  }

  private static void writeFile(Path directory, String relativePath, String content) {
    try {
      Path file = directory.resolve(relativePath);
      Files.createDirectories(file.getParent());
      Files.writeString(file, content);
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
