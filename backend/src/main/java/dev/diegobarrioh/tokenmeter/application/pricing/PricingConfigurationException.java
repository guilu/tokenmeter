package dev.diegobarrioh.tokenmeter.application.pricing;

public class PricingConfigurationException extends RuntimeException {
  public PricingConfigurationException(String message) {
    super(message);
  }

  public PricingConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
