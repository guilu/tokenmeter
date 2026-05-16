package dev.diegobarrioh.tokenmeter.application.pricing.refresh;

/**
 * Application-layer exception raised when a pricing refresh attempt cannot complete. Infrastructure
 * adapters translate transport / parsing failures into this type so {@link PricingRefreshService}
 * can react without depending on HTTP types.
 */
public class PricingRefreshException extends RuntimeException {

  public PricingRefreshException(String message) {
    super(message);
  }

  public PricingRefreshException(String message, Throwable cause) {
    super(message, cause);
  }
}
