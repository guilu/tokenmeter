package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface LeaderboardRow {
  UUID getId();

  String getRepositoryUrl();

  String getOwnerName();

  String getRepositoryName();

  Instant getCreatedAt();

  long getTotalFiles();

  long getTotalLines();

  long getTotalBytes();

  long getTotalTokens();

  long getAnalysisCount();

  String getProvider();

  String getModel();

  String getMode();

  BigDecimal getTotalCost();
}
