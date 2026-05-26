package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisJobQueryService;
import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisJobSubmissionService;
import dev.diegobarrioh.tokenmeter.application.analyzer.AnalyzeThrottleProperties;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobErrorCode;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobQueueState;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobView;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotId;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import dev.diegobarrioh.tokenmeter.infrastructure.web.PublicOriginProperties;
import dev.diegobarrioh.tokenmeter.infrastructure.web.WebMvcConfiguration;
import dev.diegobarrioh.tokenmeter.infrastructure.web.repository.RepositoryIntakeExceptionHandler;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers the eight acceptance scenarios from the {@code observable-analysis-jobs} spec plus the
 * delta added by {@code concurrent-analysis-limits} (queueState emission + phaseLabel override
 * under contention).
 */
@WebMvcTest(AnalysisJobController.class)
@Import({
  AnalysisJobResponseMapper.class,
  RepositoryIntakeExceptionHandler.class,
  AnalyzeRateLimitInterceptor.class,
  WebMvcConfiguration.class,
})
@EnableConfigurationProperties({AnalyzeThrottleProperties.class, PublicOriginProperties.class})
class AnalysisJobControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AnalysisJobSubmissionService submissionService;
  @MockitoBean private AnalysisJobQueryService queryService;

  @Test
  void postValidUrlReturns202WithJobIdAndStatusUrl() throws Exception {
    AnalysisJobId jobId = AnalysisJobId.random();
    AnalysisJobSnapshot snapshot = queuedSnapshot(jobId);
    when(submissionService.submit(anyString())).thenReturn(snapshot);

    mockMvc
        .perform(
            post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"https://github.com/guilu/tokenmeter\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value(jobId.value().toString()))
        .andExpect(jsonPath("$.status").value("QUEUED"))
        .andExpect(jsonPath("$.statusUrl").value("/api/analyze/jobs/" + jobId.value()))
        .andExpect(jsonPath("$.analysisId").doesNotExist())
        .andExpect(jsonPath("$.queueState").doesNotExist());
  }

  @Test
  void postInvalidUrlReturns400AndDoesNotPersistJobRow() throws Exception {
    when(submissionService.submit(anyString()))
        .thenThrow(
            new RepositoryIntakeException(RepositoryIntakeErrorCode.INVALID_URL, "invalid url"));

    mockMvc
        .perform(
            post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"not-a-url\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_URL"));

    verify(queryService, never()).getView(any());
  }

  @Test
  void postWhenQueueCeilingReachedReturns429() throws Exception {
    when(submissionService.submit(anyString()))
        .thenThrow(
            new RepositoryIntakeException(
                RepositoryIntakeErrorCode.RATE_LIMITED, "Analysis queue is full"));

    mockMvc
        .perform(
            post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"https://github.com/guilu/tokenmeter\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void getQueuedJobReturns200WithProgressUnderHundred() throws Exception {
    AnalysisJobId jobId = AnalysisJobId.random();
    AnalysisJobView view =
        new AnalysisJobView(queuedSnapshot(jobId), new AnalysisJobQueueState(0, 3, 1));
    when(queryService.getView(eq(jobId))).thenReturn(Optional.of(view));

    mockMvc
        .perform(get("/api/analyze/jobs/{jobId}", jobId.value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("QUEUED"))
        .andExpect(jsonPath("$.phase").value("QUEUED"))
        .andExpect(jsonPath("$.progressPercent").value(0))
        .andExpect(jsonPath("$.analysisId").doesNotExist())
        .andExpect(jsonPath("$.error").doesNotExist())
        .andExpect(jsonPath("$.pricing").doesNotExist());
  }

  @Test
  void getSuccessfulJobReturns100AndAnalysisId() throws Exception {
    AnalysisJobId jobId = AnalysisJobId.random();
    UUID analysisId = UUID.randomUUID();
    Instant now = Instant.parse("2026-05-08T20:00:00Z");
    AnalysisJobSnapshot snapshot =
        new AnalysisJobSnapshot(
            jobId,
            "https://github.com/guilu/tokenmeter",
            AnalysisJobStatus.SUCCESS,
            AnalysisJobPhase.COMPLETED,
            100,
            "All done",
            analysisId,
            null,
            null,
            new AnalysisJobMetrics(10L, 10L, 0L, 100L, 1, 1),
            now,
            now,
            now,
            now);
    when(queryService.getView(eq(jobId)))
        .thenReturn(Optional.of(new AnalysisJobView(snapshot, null)));

    mockMvc
        .perform(get("/api/analyze/jobs/{jobId}", jobId.value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.phase").value("COMPLETED"))
        .andExpect(jsonPath("$.progressPercent").value(100))
        .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
        .andExpect(jsonPath("$.error").doesNotExist())
        .andExpect(jsonPath("$.pricing").doesNotExist());
  }

  @Test
  void getJobReturnsPricingMetadataWhenCaptured() throws Exception {
    AnalysisJobId jobId = AnalysisJobId.random();
    UUID analysisId = UUID.randomUUID();
    Instant now = Instant.parse("2026-05-08T20:00:00Z");
    Instant capturedAt = Instant.parse("2026-05-24T18:42:11Z");
    AnalysisJobSnapshot snapshot =
        new AnalysisJobSnapshot(
            jobId,
            "https://github.com/guilu/tokenmeter",
            AnalysisJobStatus.SUCCESS,
            AnalysisJobPhase.COMPLETED,
            100,
            "All done",
            analysisId,
            null,
            null,
            new AnalysisJobMetrics(10L, 10L, 0L, 100L, 1, 1),
            now,
            now,
            now,
            now,
            new AnalysisJobSnapshot.PricingSnapshotCapture(
                new PricingSnapshotId(samplePricingId()), PricingSource.REMOTE, capturedAt));
    when(queryService.getView(eq(jobId)))
        .thenReturn(Optional.of(new AnalysisJobView(snapshot, null)));

    mockMvc
        .perform(get("/api/analyze/jobs/{jobId}", jobId.value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pricing.snapshotId").value(samplePricingId()))
        .andExpect(jsonPath("$.pricing.primarySource").value("REMOTE"))
        .andExpect(jsonPath("$.pricing.capturedAt").value("2026-05-24T18:42:11Z"));
  }

  @Test
  void getFailedJobReturns200WithErrorPayload() throws Exception {
    AnalysisJobId jobId = AnalysisJobId.random();
    Instant now = Instant.parse("2026-05-08T20:00:00Z");
    AnalysisJobSnapshot snapshot =
        new AnalysisJobSnapshot(
            jobId,
            "https://github.com/guilu/slow",
            AnalysisJobStatus.FAILED,
            AnalysisJobPhase.FAILED,
            10,
            null,
            null,
            AnalysisJobErrorCode.CLONE_TIMEOUT,
            "Repository clone exceeded timeout of 50 seconds",
            AnalysisJobMetrics.empty(),
            now,
            now,
            now,
            now);
    when(queryService.getView(eq(jobId)))
        .thenReturn(Optional.of(new AnalysisJobView(snapshot, null)));

    mockMvc
        .perform(get("/api/analyze/jobs/{jobId}", jobId.value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.phase").value("FAILED"))
        .andExpect(jsonPath("$.error.code").value("CLONE_TIMEOUT"))
        .andExpect(
            jsonPath("$.error.message").value("Repository clone exceeded timeout of 50 seconds"))
        .andExpect(jsonPath("$.analysisId").doesNotExist());
  }

  @Test
  void getUnknownJobIdReturns404() throws Exception {
    UUID unknownId = UUID.randomUUID();
    when(queryService.getView(any())).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/analyze/jobs/{jobId}", unknownId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
  }

  @Test
  void repeatedPollingNeverHits429BecauseInterceptorIsExcluded() throws Exception {
    AnalysisJobId jobId = AnalysisJobId.random();
    AnalysisJobView view =
        new AnalysisJobView(queuedSnapshot(jobId), new AnalysisJobQueueState(0, 3, 1));
    when(queryService.getView(eq(jobId))).thenReturn(Optional.of(view));

    for (int i = 0; i < 20; i++) {
      mockMvc.perform(get("/api/analyze/jobs/{jobId}", jobId.value())).andExpect(status().isOk());
    }
  }

  @Test
  void getJobIncludesQueueStateWhenQueued() throws Exception {
    AnalysisJobId jobId = AnalysisJobId.random();
    AnalysisJobView view =
        new AnalysisJobView(queuedSnapshot(jobId), new AnalysisJobQueueState(1, 3, 2));
    when(queryService.getView(eq(jobId))).thenReturn(Optional.of(view));

    mockMvc
        .perform(get("/api/analyze/jobs/{jobId}", jobId.value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.queueState.runningCount").value(1))
        .andExpect(jsonPath("$.queueState.maxConcurrency").value(3))
        .andExpect(jsonPath("$.queueState.queuePosition").value(2))
        .andExpect(jsonPath("$.phaseLabel").value("Queued"));
  }

  @Test
  void getJobPhaseLabelWaitingWhenSlotContention() throws Exception {
    AnalysisJobId jobId = AnalysisJobId.random();
    AnalysisJobView view =
        new AnalysisJobView(queuedSnapshot(jobId), new AnalysisJobQueueState(2, 2, 1));
    when(queryService.getView(eq(jobId))).thenReturn(Optional.of(view));

    mockMvc
        .perform(get("/api/analyze/jobs/{jobId}", jobId.value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.queueState.runningCount").value(2))
        .andExpect(jsonPath("$.queueState.maxConcurrency").value(2))
        .andExpect(jsonPath("$.queueState.queuePosition").value(1))
        .andExpect(jsonPath("$.phaseLabel").value("Waiting for an analysis slot"));
  }

  @Test
  void getJobOmitsQueueStateForTerminalJob() throws Exception {
    AnalysisJobId jobId = AnalysisJobId.random();
    UUID analysisId = UUID.randomUUID();
    Instant now = Instant.parse("2026-05-08T20:00:00Z");
    AnalysisJobSnapshot snapshot =
        new AnalysisJobSnapshot(
            jobId,
            "https://github.com/guilu/tokenmeter",
            AnalysisJobStatus.SUCCESS,
            AnalysisJobPhase.COMPLETED,
            100,
            "All done",
            analysisId,
            null,
            null,
            new AnalysisJobMetrics(10L, 10L, 0L, 100L, 1, 1),
            now,
            now,
            now,
            now);
    when(queryService.getView(eq(jobId)))
        .thenReturn(Optional.of(new AnalysisJobView(snapshot, null)));

    mockMvc
        .perform(get("/api/analyze/jobs/{jobId}", jobId.value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.queueState").doesNotExist());
  }

  private static AnalysisJobSnapshot queuedSnapshot(AnalysisJobId id) {
    Instant now = Instant.parse("2026-05-08T20:00:00Z");
    return new AnalysisJobSnapshot(
        id,
        "https://github.com/guilu/tokenmeter",
        AnalysisJobStatus.QUEUED,
        AnalysisJobPhase.QUEUED,
        0,
        null,
        null,
        null,
        null,
        AnalysisJobMetrics.empty(),
        now,
        null,
        now,
        null);
  }

  private static String samplePricingId() {
    return "v1:" + "0".repeat(64);
  }
}
