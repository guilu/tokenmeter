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
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
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
 * Covers the eight acceptance scenarios from the {@code observable-analysis-jobs} spec for the new
 * {@link AnalysisJobController}.
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
        .andExpect(jsonPath("$.analysisId").doesNotExist());
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

    verify(queryService, never()).findById(any());
  }

  @Test
  void postWhenExecutorSaturatedReturns429RateLimited() throws Exception {
    when(submissionService.submit(anyString()))
        .thenThrow(
            new RepositoryIntakeException(RepositoryIntakeErrorCode.RATE_LIMITED, "queue full"));

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
    AnalysisJobSnapshot snapshot = queuedSnapshot(jobId);
    when(queryService.findById(eq(jobId))).thenReturn(Optional.of(snapshot));

    mockMvc
        .perform(get("/api/analyze/jobs/{jobId}", jobId.value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("QUEUED"))
        .andExpect(jsonPath("$.phase").value("QUEUED"))
        .andExpect(jsonPath("$.progressPercent").value(0))
        .andExpect(jsonPath("$.analysisId").doesNotExist())
        .andExpect(jsonPath("$.error").doesNotExist());
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
    when(queryService.findById(eq(jobId))).thenReturn(Optional.of(snapshot));

    mockMvc
        .perform(get("/api/analyze/jobs/{jobId}", jobId.value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.phase").value("COMPLETED"))
        .andExpect(jsonPath("$.progressPercent").value(100))
        .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
        .andExpect(jsonPath("$.error").doesNotExist());
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
    when(queryService.findById(eq(jobId))).thenReturn(Optional.of(snapshot));

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
    when(queryService.findById(any())).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/analyze/jobs/{jobId}", unknownId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
  }

  @Test
  void repeatedPollingNeverHits429BecauseInterceptorIsExcluded() throws Exception {
    AnalysisJobId jobId = AnalysisJobId.random();
    when(queryService.findById(eq(jobId))).thenReturn(Optional.of(queuedSnapshot(jobId)));

    for (int i = 0; i < 20; i++) {
      mockMvc.perform(get("/api/analyze/jobs/{jobId}", jobId.value())).andExpect(status().isOk());
    }
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
}
