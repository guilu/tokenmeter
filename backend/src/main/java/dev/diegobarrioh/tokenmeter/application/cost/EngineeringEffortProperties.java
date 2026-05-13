package dev.diegobarrioh.tokenmeter.application.cost;

import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tokenmeter.engineering-effort")
public class EngineeringEffortProperties {
  private BigDecimal tokensPerSeniorEngineerHour = new BigDecimal("18000");
  private BigDecimal hoursPerEngineeringDay = new BigDecimal("6.5");
  private Map<CostEstimationMode, BigDecimal> modeComplexityMultipliers = defaultMultipliers();

  public BigDecimal getTokensPerSeniorEngineerHour() {
    return tokensPerSeniorEngineerHour;
  }

  public void setTokensPerSeniorEngineerHour(BigDecimal tokensPerSeniorEngineerHour) {
    this.tokensPerSeniorEngineerHour = tokensPerSeniorEngineerHour;
  }

  public BigDecimal getHoursPerEngineeringDay() {
    return hoursPerEngineeringDay;
  }

  public void setHoursPerEngineeringDay(BigDecimal hoursPerEngineeringDay) {
    this.hoursPerEngineeringDay = hoursPerEngineeringDay;
  }

  public Map<CostEstimationMode, BigDecimal> getModeComplexityMultipliers() {
    return modeComplexityMultipliers;
  }

  public void setModeComplexityMultipliers(
      Map<CostEstimationMode, BigDecimal> modeComplexityMultipliers) {
    EnumMap<CostEstimationMode, BigDecimal> merged = defaultMultipliers();
    if (modeComplexityMultipliers != null) {
      merged.putAll(modeComplexityMultipliers);
    }
    this.modeComplexityMultipliers = merged;
  }

  public BigDecimal multiplierFor(CostEstimationMode mode) {
    return modeComplexityMultipliers.getOrDefault(mode, BigDecimal.ONE);
  }

  private static EnumMap<CostEstimationMode, BigDecimal> defaultMultipliers() {
    EnumMap<CostEstimationMode, BigDecimal> defaults = new EnumMap<>(CostEstimationMode.class);
    defaults.put(CostEstimationMode.RAW, new BigDecimal("0.55"));
    defaults.put(CostEstimationMode.ASSISTED, new BigDecimal("0.80"));
    defaults.put(CostEstimationMode.AGENTIC, BigDecimal.ONE);
    return defaults;
  }
}
