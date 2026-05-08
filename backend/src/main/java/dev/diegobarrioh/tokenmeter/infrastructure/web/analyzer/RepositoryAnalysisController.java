package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RepositoryAnalysisController {
  private final RepositoryAnalysisService analysisService;
  private final RepositoryAnalysisMapper mapper;

  public RepositoryAnalysisController(
      RepositoryAnalysisService analysisService, RepositoryAnalysisMapper mapper) {
    this.analysisService = analysisService;
    this.mapper = mapper;
  }

  @PostMapping("/api/analyze")
  @ResponseStatus(HttpStatus.OK)
  public RepositoryAnalysisResponse analyze(@Valid @RequestBody RepositoryAnalysisRequest request) {
    return mapper.toResponse(analysisService.analyze(request.repositoryUrl()));
  }
}
