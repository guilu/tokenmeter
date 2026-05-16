package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshException;

/**
 * Infrastructure-level exception raised when the LiteLLM upstream call fails (connection issue,
 * non-2xx status, malformed body, empty payload). Extends {@link PricingRefreshException} so the
 * application service can react with a single catch.
 */
public class PricingFetchException extends PricingRefreshException {

  public PricingFetchException(String message) {
    super(message);
  }

  public PricingFetchException(String message, Throwable cause) {
    super(message, cause);
  }
}
