package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshException;
import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin {@code @Scheduled} entry-point that delegates to {@link PricingRefreshService#refresh()}.
 * Swallows {@link PricingRefreshException} and {@link DataAccessException} so a single failure does
 * not kill the schedule; the service has already incremented the failure counter and logged the
 * underlying cause.
 *
 * <p>When {@code tokenmeter.pricing.refresh.on-startup} is set, the bean also triggers a refresh
 * once the {@link ApplicationReadyEvent} fires. This is intentionally feature-gated so prod /
 * docker can stay disabled by default (see design §7.2).
 */
@Component
@ConditionalOnProperty(
    prefix = "tokenmeter.pricing.refresh",
    name = "enabled",
    havingValue = "true")
public class PricingRefreshScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(PricingRefreshScheduler.class);

  private final PricingRefreshService service;
  private final PricingProperties properties;

  public PricingRefreshScheduler(PricingRefreshService service, PricingProperties properties) {
    this.service = service;
    this.properties = properties;
  }

  @Scheduled(
      cron = "${tokenmeter.pricing.refresh.cron}",
      zone = "${tokenmeter.pricing.refresh.zone:UTC}")
  public void runScheduled() {
    safeRefresh();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onStartup() {
    if (!properties.refresh().onStartup()) {
      return;
    }
    LOG.info("Triggering startup pricing refresh because on-startup=true");
    safeRefresh();
  }

  private void safeRefresh() {
    try {
      service.refresh();
    } catch (PricingRefreshException ex) {
      LOG.warn("Scheduled pricing refresh skipped due to upstream failure: {}", ex.getMessage());
    } catch (DataAccessException ex) {
      LOG.warn("Scheduled pricing refresh skipped due to persistence failure: {}", ex.getMessage());
    }
  }
}
