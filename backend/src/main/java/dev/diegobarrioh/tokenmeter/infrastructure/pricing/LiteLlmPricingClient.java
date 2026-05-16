package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import dev.diegobarrioh.tokenmeter.infrastructure.pricing.litellm.LiteLlmModelEntry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin synchronous HTTP client that fetches the LiteLLM pricing catalogue. The {@link RestClient}
 * is pre-configured (timeout, base URL) in {@code PricingConfig#pricingLiteLlmClient}.
 *
 * <p>Any transport / parsing failure surfaces as a {@link PricingFetchException} so callers never
 * observe Spring's HTTP exception hierarchy.
 */
@Component
public class LiteLlmPricingClient {

  private static final Logger LOG = LoggerFactory.getLogger(LiteLlmPricingClient.class);

  private static final ParameterizedTypeReference<Map<String, LiteLlmModelEntry>> RESPONSE_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;

  public LiteLlmPricingClient(RestClient pricingLiteLlmClient) {
    this.restClient = pricingLiteLlmClient;
  }

  /**
   * Performs the upstream {@code GET}. The returned map's keys are LiteLLM model identifiers (e.g.
   * {@code gpt-4o}, {@code anthropic/claude-3-opus}). Callers are responsible for ignoring the
   * {@code sample_spec} synthetic entry.
   */
  public Map<String, LiteLlmModelEntry> fetch() {
    try {
      Map<String, LiteLlmModelEntry> body = restClient.get().retrieve().body(RESPONSE_TYPE);
      if (body == null || body.isEmpty()) {
        throw new PricingFetchException("empty LiteLLM response");
      }
      LOG.debug("Fetched {} LiteLLM pricing entries", Integer.valueOf(body.size()));
      return body;
    } catch (RestClientException ex) {
      throw new PricingFetchException("failed to fetch LiteLLM pricing", ex);
    }
  }
}
