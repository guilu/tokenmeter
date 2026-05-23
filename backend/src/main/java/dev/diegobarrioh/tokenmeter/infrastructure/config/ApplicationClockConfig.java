package dev.diegobarrioh.tokenmeter.infrastructure.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes a {@link Clock} bean so services depending on time (job emitters, retention scheduler,
 * reaper) can be tested with a fixed clock. Tests may override via {@code @MockBean} or by
 * declaring their own bean — {@link ConditionalOnMissingBean} keeps that ergonomic.
 */
@Configuration(proxyBeanMethods = false)
public class ApplicationClockConfig {

  @Bean
  @ConditionalOnMissingBean
  public Clock systemClock() {
    return Clock.systemUTC();
  }
}
