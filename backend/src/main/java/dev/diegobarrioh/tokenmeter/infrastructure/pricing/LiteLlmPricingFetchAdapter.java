package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingFetchPort;
import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingFetchResult;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.LiteLlmPricingMapper.MappingResult;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.litellm.LiteLlmModelEntry;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter that wires {@link LiteLlmPricingClient} + {@link LiteLlmPricingMapper}
 * behind the application-layer {@link PricingFetchPort}. This keeps the refresh service free of
 * HTTP / mapping concerns.
 */
@Component
public class LiteLlmPricingFetchAdapter implements PricingFetchPort {

  private final LiteLlmPricingClient client;
  private final LiteLlmPricingMapper mapper;

  public LiteLlmPricingFetchAdapter(LiteLlmPricingClient client, LiteLlmPricingMapper mapper) {
    this.client = client;
    this.mapper = mapper;
  }

  @Override
  public PricingFetchResult fetchAndMap(OffsetDateTime fetchedAt) {
    Map<String, LiteLlmModelEntry> raw = client.fetch();
    MappingResult result = mapper.mapToSnapshots(raw, fetchedAt);
    return new PricingFetchResult(result.snapshots(), result.skipped());
  }
}
