package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Registers {@link PricingProperties} plus the shared {@link RestClient} and {@link Clock} beans
 * the pricing pipeline relies on. Scheduling-related wiring lives in {@code
 * PricingSchedulingConfig} so it can be conditionally activated.
 */
@Configuration
@EnableConfigurationProperties(PricingProperties.class)
public class PricingConfig {

  /**
   * Project-scoped {@link RestClient} pre-configured for the LiteLLM upstream: connect/read
   * timeouts taken from {@code tokenmeter.pricing.litellm.timeout} and base URL pointed at the raw
   * JSON catalogue. Kept package-scope-agnostic so any future consumer can inject it by name.
   */
  @Bean
  public RestClient pricingLiteLlmClient(RestClient.Builder builder, PricingProperties properties) {
    PricingProperties.LiteLlm settings = properties.litellm();
    Duration timeout = settings.timeout();
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout((int) timeout.toMillis());
    requestFactory.setReadTimeout((int) timeout.toMillis());
    return builder
        .baseUrl(settings.url().toString())
        .requestFactory(requestFactory)
        .defaultHeader("Accept", "application/json")
        .defaultHeader("User-Agent", "tokenmeter/1.0 (+https://github.com/diegobarrioh/tokenmeter)")
        .build();
  }

  /**
   * UTC clock used by the refresh service to stamp every snapshot in a batch with the same instant.
   * Marked as {@code @ConditionalOnMissingBean} so test contexts can supply their own fixed clock
   * if they need deterministic timestamps.
   */
  @Bean
  @ConditionalOnMissingBean(Clock.class)
  public Clock pricingClock() {
    return Clock.systemUTC();
  }
}
