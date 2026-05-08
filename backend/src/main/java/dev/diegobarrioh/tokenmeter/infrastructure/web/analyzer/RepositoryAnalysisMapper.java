package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import org.springframework.stereotype.Component;

@Component
public class RepositoryAnalysisMapper {
  public RepositoryAnalysisResponse toResponse(RepositoryAnalysisResult result) {
    var scan = result.scan();
    return new RepositoryAnalysisResponse(
        result.repositoryUrl(),
        RepositoryAnalysisStatus.SUCCESS,
        new RepositoryAnalysisMetricsResponse(
            scan.totalFiles(), scan.totalLines(), scan.totalBytes(), scan.languages()));
  }
}
