package dev.diegobarrioh.tokenmeter.infrastructure.web.pricing;

import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Scoped exception handler for {@link PricingAdminController}. Maps refresh-pipeline failures to
 * {@code 503 Service Unavailable} so transient upstream issues do not surface as {@code 500}. The
 * read endpoint ({@link PricingController}) is intentionally NOT covered by this advice.
 */
@RestControllerAdvice(assignableTypes = PricingAdminController.class)
public class PricingExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(PricingExceptionHandler.class);

  @ExceptionHandler(PricingRefreshException.class)
  public ResponseEntity<ErrorResponse> handleRefreshException(PricingRefreshException ex) {
    LOG.warn("Pricing admin refresh failed: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse("pricing_refresh_failed", ex.getMessage()));
  }

  /** Minimal JSON error envelope: {@code {"error": "...", "message": "..."}}. */
  public record ErrorResponse(String error, String message) {}
}
