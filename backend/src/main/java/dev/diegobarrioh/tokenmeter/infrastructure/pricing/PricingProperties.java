package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration block bound to {@code tokenmeter.pricing.*}. Defaults match design §7.1: refresh is
 * disabled out of the box and the admin endpoint is opt-in via {@link Admin#enabled()}.
 */
@ConfigurationProperties("tokenmeter.pricing")
public record PricingProperties(
    String configLocation,
    String mappingLocation,
    String overridesLocation,
    LiteLlm litellm,
    Refresh refresh,
    Admin admin) {

  public PricingProperties {
    if (configLocation == null || configLocation.isBlank()) {
      configLocation = "classpath:pricing.yaml";
    }
    if (mappingLocation == null || mappingLocation.isBlank()) {
      mappingLocation = "classpath:pricing-mapping.yaml";
    }
    if (overridesLocation == null || overridesLocation.isBlank()) {
      overridesLocation = "classpath:pricing-overrides.yaml";
    }
    if (litellm == null) {
      litellm = LiteLlm.defaults();
    }
    if (refresh == null) {
      refresh = Refresh.defaults();
    }
    if (admin == null) {
      admin = Admin.defaults();
    }
  }

  /** LiteLLM upstream connection settings. */
  public record LiteLlm(URI url, Duration timeout) {

    private static final URI DEFAULT_URL =
        URI.create(
            "https://raw.githubusercontent.com/BerriAI/litellm/main"
                + "/model_prices_and_context_window.json");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    public LiteLlm {
      if (url == null) {
        url = DEFAULT_URL;
      }
      if (timeout == null) {
        timeout = DEFAULT_TIMEOUT;
      }
    }

    static LiteLlm defaults() {
      return new LiteLlm(DEFAULT_URL, DEFAULT_TIMEOUT);
    }
  }

  /** Scheduled refresh settings. */
  public record Refresh(boolean enabled, String cron, String zone, boolean onStartup) {

    private static final String DEFAULT_CRON = "0 0 3 * * MON";
    private static final String DEFAULT_ZONE = "UTC";

    public Refresh {
      if (cron == null || cron.isBlank()) {
        cron = DEFAULT_CRON;
      }
      if (zone == null || zone.isBlank()) {
        zone = DEFAULT_ZONE;
      }
    }

    static Refresh defaults() {
      return new Refresh(false, DEFAULT_CRON, DEFAULT_ZONE, false);
    }
  }

  /** Admin endpoint feature flag. */
  public record Admin(boolean enabled) {

    static Admin defaults() {
      return new Admin(true);
    }
  }
}
