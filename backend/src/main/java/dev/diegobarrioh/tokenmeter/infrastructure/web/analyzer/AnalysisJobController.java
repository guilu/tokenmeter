package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisJobNotFoundException;
import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisJobQueryService;
import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisJobSubmissionService;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dedicated controller for the async analysis job API: {@code POST /api/analyze} (submission) and
 * {@code GET /api/analyze/jobs/{jobId}} (polling). The polling endpoint is excluded from the
 * analyze rate limiter via {@link
 * dev.diegobarrioh.tokenmeter.infrastructure.web.WebMvcConfiguration}.
 */
@RestController
public class AnalysisJobController {

  private final AnalysisJobSubmissionService submissionService;
  private final AnalysisJobQueryService queryService;
  private final AnalysisJobResponseMapper mapper;

  public AnalysisJobController(
      AnalysisJobSubmissionService submissionService,
      AnalysisJobQueryService queryService,
      AnalysisJobResponseMapper mapper) {
    this.submissionService = submissionService;
    this.queryService = queryService;
    this.mapper = mapper;
  }

  @PostMapping("/api/analyze")
  public ResponseEntity<AnalysisJobAcceptedResponse> submit(
      @Valid @RequestBody RepositoryAnalysisRequest request) {
    AnalysisJobSnapshot snapshot = submissionService.submit(request.repositoryUrl());
    return ResponseEntity.accepted().body(mapper.toAccepted(snapshot));
  }

  @GetMapping("/api/analyze/jobs/{jobId}")
  public AnalysisJobStatusResponse getJob(@PathVariable UUID jobId) {
    AnalysisJobId id = new AnalysisJobId(jobId);
    return queryService
        .getView(id)
        .map(mapper::toStatus)
        .orElseThrow(() -> new AnalysisJobNotFoundException(id));
  }
}
