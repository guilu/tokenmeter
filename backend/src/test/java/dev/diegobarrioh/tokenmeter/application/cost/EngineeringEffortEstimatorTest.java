package dev.diegobarrioh.tokenmeter.application.cost;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EngineeringEffortEstimatorTest {
  @Test
  void estimatesSeniorHoursAndDaysFromTokenScaleAndMode() {
    var properties = new EngineeringEffortProperties();
    properties.setTokensPerSeniorEngineerHour(new BigDecimal("1000"));
    properties.setHoursPerEngineeringDay(new BigDecimal("5"));
    properties.setModeComplexityMultipliers(
        Map.of(CostEstimationMode.ASSISTED, new BigDecimal("0.50")));
    var estimator = new EngineeringEffortEstimator(properties);

    var estimate = estimator.estimate(costEstimate(CostEstimationMode.ASSISTED, 2_000, 10_000));

    assertThat(estimate.seniorEngineerHours()).isEqualByComparingTo("5.20");
    assertThat(estimate.engineeringDays()).isEqualByComparingTo("1.04");
    assertThat(estimate.manualImplementationEffort()).isEqualTo("5.2 senior engineer hours");
    assertThat(estimate.summary()).isEqualTo("Equivalent to 5.2 senior engineer hours");
    assertThat(estimate.assumptions().modeComplexityMultiplier()).isEqualByComparingTo("0.50");
  }

  @Test
  void formatsLargeEstimatesAsEngineeringDays() {
    var estimator = new EngineeringEffortEstimator(new EngineeringEffortProperties());

    var estimate = estimator.estimate(costEstimate(CostEstimationMode.AGENTIC, 10_000, 720_000));

    assertThat(estimate.seniorEngineerHours()).isEqualByComparingTo("40.11");
    assertThat(estimate.engineeringDays()).isEqualByComparingTo("6.17");
    assertThat(estimate.manualImplementationEffort()).isEqualTo("6.2 engineering days");
  }

  private static ModelCostEstimate costEstimate(
      CostEstimationMode mode, long estimatedInputTokens, long estimatedOutputTokens) {
    return new ModelCostEstimate(
        AiProvider.OPENAI,
        "gpt-4o",
        mode,
        1_000,
        estimatedInputTokens,
        estimatedOutputTokens,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "test formula");
  }
}
