package dev.diegobarrioh.tokenmeter.application.analyzer;

import java.util.UUID;

public class AnalysisNotFoundException extends RuntimeException {
  private final UUID analysisId;

  public AnalysisNotFoundException(UUID analysisId) {
    super("Analysis not found: " + analysisId);
    this.analysisId = analysisId;
  }

  public UUID analysisId() {
    return analysisId;
  }
}
