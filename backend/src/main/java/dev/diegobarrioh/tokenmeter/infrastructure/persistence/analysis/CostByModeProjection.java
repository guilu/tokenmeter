package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import java.math.BigDecimal;

public interface CostByModeProjection {
  String getMode();

  BigDecimal getTotalCost();

  long getAnalysisCount();
}
