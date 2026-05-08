package dev.diegobarrioh.tokenmeter.domain.cost;

import java.math.BigDecimal;

public enum CostEstimationMode {
  RAW(new BigDecimal("1"), new BigDecimal("0")),
  ASSISTED(new BigDecimal("5"), new BigDecimal("1")),
  AGENTIC(new BigDecimal("20"), new BigDecimal("4"));

  private final BigDecimal outputMultiplier;
  private final BigDecimal reasoningInputMultiplier;

  CostEstimationMode(BigDecimal outputMultiplier, BigDecimal reasoningInputMultiplier) {
    this.outputMultiplier = outputMultiplier;
    this.reasoningInputMultiplier = reasoningInputMultiplier;
  }

  public BigDecimal outputMultiplier() {
    return outputMultiplier;
  }

  public BigDecimal reasoningInputMultiplier() {
    return reasoningInputMultiplier;
  }
}
