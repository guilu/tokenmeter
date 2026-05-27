package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

public interface LanguageInsightProjection {
  String getLanguageName();

  long getTotalTokens();

  long getRepoCount();
}
