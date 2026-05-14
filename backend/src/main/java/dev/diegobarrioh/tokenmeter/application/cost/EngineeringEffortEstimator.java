package dev.diegobarrioh.tokenmeter.application.cost;

import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class EngineeringEffortEstimator {
  private static final BigDecimal INPUT_CONTEXT_WEIGHT = new BigDecimal("0.20");
  private static final BigDecimal SIXTY = new BigDecimal("60");

  private final EngineeringEffortProperties properties;

  public EngineeringEffortEstimator(EngineeringEffortProperties properties) {
    this.properties = properties;
  }

  public EngineeringEffortEstimate estimate(ModelCostEstimate costEstimate) {
    BigDecimal modeMultiplier = properties.multiplierFor(costEstimate.mode());
    BigDecimal weightedTokens =
        BigDecimal.valueOf(costEstimate.estimatedOutputTokens())
            .add(
                BigDecimal.valueOf(costEstimate.estimatedInputTokens())
                    .multiply(INPUT_CONTEXT_WEIGHT));
    BigDecimal hours =
        weightedTokens
            .multiply(modeMultiplier)
            .divide(properties.getTokensPerSeniorEngineerHour(), 2, RoundingMode.HALF_UP);
    BigDecimal days = hours.divide(properties.getHoursPerEngineeringDay(), 2, RoundingMode.HALF_UP);

    var assumptions =
        new EngineeringEffortAssumptions(
            properties.getTokensPerSeniorEngineerHour(),
            properties.getHoursPerEngineeringDay(),
            modeMultiplier);
    String effortLabel = effortLabel(hours, days);
    return new EngineeringEffortEstimate(
        hours,
        days,
        effortLabel,
        "Equivalent to %s".formatted(effortLabel),
        "((estimatedOutputTokens + estimatedInputTokens*0.20) * modeComplexityMultiplier) / tokensPerSeniorEngineerHour",
        assumptions);
  }

  private static String effortLabel(BigDecimal hours, BigDecimal days) {
    if (hours.compareTo(new BigDecimal("0.10")) < 0) {
      return "less than 10 minutes of senior engineering work";
    }
    if (hours.compareTo(BigDecimal.ONE) < 0) {
      BigDecimal minutes = hours.multiply(SIXTY).setScale(0, RoundingMode.HALF_UP);
      return "about %s minutes of senior engineering work".formatted(minutes.toPlainString());
    }
    if (hours.compareTo(new BigDecimal("6.5")) < 0) {
      return String.format(Locale.ROOT, "%.1f senior engineer hours", hours.doubleValue());
    }
    if (days.compareTo(new BigDecimal("1.25")) < 0) {
      return "about 1 engineering day";
    }
    return String.format(Locale.ROOT, "%.1f engineering days", days.doubleValue());
  }
}
