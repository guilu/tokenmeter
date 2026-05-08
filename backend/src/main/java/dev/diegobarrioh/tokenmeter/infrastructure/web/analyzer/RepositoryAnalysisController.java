package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RepositoryAnalysisController {
  private final RepositoryAnalysisService analysisService;
  private final RepositoryAnalysisMapper mapper;
  private final CostBreakdownMapper costBreakdownMapper;

  public RepositoryAnalysisController(
      RepositoryAnalysisService analysisService,
      RepositoryAnalysisMapper mapper,
      CostBreakdownMapper costBreakdownMapper) {
    this.analysisService = analysisService;
    this.mapper = mapper;
    this.costBreakdownMapper = costBreakdownMapper;
  }

  @PostMapping("/api/analyze")
  @ResponseStatus(HttpStatus.OK)
  public RepositoryAnalysisResponse analyze(@Valid @RequestBody RepositoryAnalysisRequest request) {
    return mapper.toResponse(analysisService.analyze(request.repositoryUrl()));
  }

  @GetMapping("/api/analyze/{id}")
  public RepositoryAnalysisResponse findById(@PathVariable UUID id) {
    return mapper.toResponse(analysisService.findById(id));
  }

  @GetMapping("/api/analyze/{id}/cost-breakdown")
  public CostBreakdownResponse getCostBreakdown(@PathVariable UUID id) {
    return costBreakdownMapper.toResponse(analysisService.findById(id));
  }
}
