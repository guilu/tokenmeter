package dev.diegobarrioh.tokenmeter.application.analyzer;

import java.util.Optional;
import java.util.UUID;

public interface AnalysisPersistenceService {
  RepositoryAnalysisResult save(RepositoryAnalysisResult result);

  Optional<RepositoryAnalysisResult> findById(UUID id);
}
