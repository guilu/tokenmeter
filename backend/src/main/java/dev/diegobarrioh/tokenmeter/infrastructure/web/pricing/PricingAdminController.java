package dev.diegobarrioh.tokenmeter.infrastructure.web.pricing;

import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshResult;
import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshService;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.PricingProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoint that triggers an on-demand pricing refresh.
 *
 * <p>Phase 4.5: controller always loaded; behaviour gated on {@code properties.admin().enabled()}.
 * When the flag is {@code false} the endpoint returns {@code 503 Service Unavailable} without
 * invoking the refresh service, ensuring the upstream is not contacted and no DB row is mutated.
 *
 * <p>On invocation, the controller delegates to {@link PricingRefreshService#refresh()} and returns
 * {@code 202 Accepted} with the resulting counts. Upstream / persistence failures bubble up as
 * {@code PricingRefreshException} (or its subclass {@code PricingFetchException}) and are mapped to
 * {@code 503} by {@link PricingExceptionHandler}.
 */
@RestController
@RequestMapping("/api/admin/pricing")
public class PricingAdminController {

  private final PricingRefreshService refreshService;
  private final PricingProperties properties;

  public PricingAdminController(
      PricingRefreshService refreshService, PricingProperties properties) {
    this.refreshService = refreshService;
    this.properties = properties;
  }

  @PostMapping("/refresh")
  public ResponseEntity<PricingRefreshResult> refresh() {
    if (!properties.admin().enabled()) {
      return ResponseEntity.status(503).build();
    }
    PricingRefreshResult result = refreshService.refresh();
    return ResponseEntity.accepted().body(result);
  }
}
