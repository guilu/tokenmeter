package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates Spring's {@code TaskScheduler} only when {@code tokenmeter.pricing.refresh.enabled} is
 * {@code true}. Keeping {@link EnableScheduling} on a dedicated, opt-in {@code @Configuration}
 * means tests and developer-mode boots stay free of an idle scheduler thread.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "tokenmeter.pricing.refresh",
    name = "enabled",
    havingValue = "true")
public class PricingSchedulingConfig {}
