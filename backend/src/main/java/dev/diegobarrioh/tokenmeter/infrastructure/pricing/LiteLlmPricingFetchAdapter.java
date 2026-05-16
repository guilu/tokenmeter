package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingFetchPort;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.litellm.LiteLlmModelEntry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter that wires {@link LiteLlmPricingClient} + {@link LiteLlmPricingMapper} +
 * {@link PricingMappingLoader} behind the application-layer {@link PricingFetchPort}. This keeps
 * the refresh service free of HTTP / mapping concerns.
 */
@Component
public class LiteLlmPricingFetchAdapter implements PricingFetchPort {

  private final LiteLlmPricingClient client;
  private final LiteLlmPricingMapper mapper;
  private final PricingMappingLoader mappingLoader;

  public LiteLlmPricingFetchAdapter(
      LiteLlmPricingClient client,
      LiteLlmPricingMapper mapper,
      PricingMappingLoader mappingLoader) {
    this.client = client;
    this.mapper = mapper;
    this.mappingLoader = mappingLoader;
  }

  @Override
  public List<PricingSnapshot> fetchAndMap(OffsetDateTime fetchedAt) {
    Map<String, LiteLlmModelEntry> raw = client.fetch();
    return mapper.mapToSnapshots(raw, fetchedAt);
  }

  @Override
  public int configuredMappingCount() {
    return mappingLoader.mappings().size();
  }
}
