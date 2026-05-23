package dev.diegobarrioh.tokenmeter.application.analyzer;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Thin read-only façade over the {@link AnalysisPersistenceService} used by the web layer to
 * resolve a completed analysis by id. The full clone → tokenize → estimate orchestration now lives
 * in {@link AnalysisJobExecutionService}; submission goes through {@link
 * AnalysisJobSubmissionService}.
 */
@Service
public class RepositoryAnalysisService {

  private final AnalysisPersistenceService persistenceService;

  public RepositoryAnalysisService(AnalysisPersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  public RepositoryAnalysisResult findById(UUID id) {
    return persistenceService.findById(id).orElseThrow(() -> new AnalysisNotFoundException(id));
  }
}
