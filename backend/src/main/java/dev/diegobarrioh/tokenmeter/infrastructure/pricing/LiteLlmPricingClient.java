package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.litellm.LiteLlmModelEntry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin synchronous HTTP client that fetches the LiteLLM pricing catalogue. The {@link RestClient}
 * is pre-configured (timeout, base URL) in {@code PricingConfig#pricingLiteLlmClient}.
 *
 * <p>The upstream is served from GitHub raw with {@code Content-Type: text/plain; charset=utf-8}
 * for {@code .json} files, so Spring's default Jackson {@code HttpMessageConverter} refuses to
 * negotiate it. We sidestep content-type negotiation by reading the body as a {@link String} and
 * deserialising it directly with the shared {@link ObjectMapper}.
 *
 * <p>Any transport / parsing failure surfaces as a {@link PricingFetchException} so callers never
 * observe Spring's HTTP exception hierarchy.
 */
@Component
public class LiteLlmPricingClient {

  private static final Logger LOG = LoggerFactory.getLogger(LiteLlmPricingClient.class);

  private static final TypeReference<Map<String, LiteLlmModelEntry>> RESPONSE_TYPE =
      new TypeReference<>() {};

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public LiteLlmPricingClient(RestClient pricingLiteLlmClient, ObjectMapper objectMapper) {
    this.restClient = pricingLiteLlmClient;
    this.objectMapper = objectMapper;
  }

  /**
   * Performs the upstream {@code GET}. The returned map's keys are LiteLLM model identifiers (e.g.
   * {@code gpt-4o}, {@code anthropic/claude-3-opus}). Callers are responsible for ignoring the
   * {@code sample_spec} synthetic entry.
   */
  public Map<String, LiteLlmModelEntry> fetch() {
    String payload;
    try {
      payload = restClient.get().retrieve().body(String.class);
    } catch (RestClientException ex) {
      throw new PricingFetchException("failed to fetch LiteLLM pricing", ex);
    }

    if (payload == null || payload.isBlank()) {
      throw new PricingFetchException("empty LiteLLM response");
    }

    Map<String, LiteLlmModelEntry> body;
    try {
      body = objectMapper.readValue(payload, RESPONSE_TYPE);
    } catch (JsonProcessingException ex) {
      throw new PricingFetchException("failed to parse LiteLLM pricing payload", ex);
    }

    if (body == null || body.isEmpty()) {
      throw new PricingFetchException("empty LiteLLM response");
    }
    LOG.debug("Fetched {} LiteLLM pricing entries", Integer.valueOf(body.size()));
    return body;
  }
}
