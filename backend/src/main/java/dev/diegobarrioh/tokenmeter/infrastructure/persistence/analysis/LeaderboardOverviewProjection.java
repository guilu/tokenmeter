package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

public interface LeaderboardOverviewProjection {
  long getTotalRepos();

  long getTotalAnalyses();

  long getTotalTokens();

  long getTotalBytes();
}
