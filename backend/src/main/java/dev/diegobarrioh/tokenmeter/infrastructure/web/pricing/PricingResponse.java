package dev.diegobarrioh.tokenmeter.infrastructure.web.pricing;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Wire envelope for {@code GET /api/pricing}. {@code lastRefreshedAt} is the most recent {@code
 * fetchedAt} among {@link PricingSource#REMOTE} snapshots, or {@code null} when no remote refresh
 * has succeeded. {@code primarySource} is {@code "litellm"} when every snapshot is REMOTE, {@code
 * "fallback"} when every snapshot is FALLBACK, and {@code "mixed"} otherwise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PricingResponse(
    OffsetDateTime lastRefreshedAt, String primarySource, List<PricingModelResponse> models) {

  /**
   * Builds a {@link PricingResponse} from the merged snapshot list. The caller is responsible for
   * having sorted {@code snapshots} in the desired display order.
   */
  public static PricingResponse from(List<PricingSnapshot> snapshots) {
    OffsetDateTime lastRefreshedAt =
        snapshots.stream()
            .filter(snapshot -> snapshot.source() == PricingSource.REMOTE)
            .map(PricingSnapshot::fetchedAt)
            .max(Comparator.naturalOrder())
            .orElse(null);

    String primarySource = derivePrimarySource(snapshots);

    List<PricingModelResponse> models = snapshots.stream().map(PricingResponse::toModel).toList();

    return new PricingResponse(lastRefreshedAt, primarySource, models);
  }

  private static String derivePrimarySource(List<PricingSnapshot> snapshots) {
    if (snapshots.isEmpty()) {
      return "fallback";
    }
    boolean anyRemote = false;
    boolean anyFallback = false;
    boolean anyOverride = false;
    for (PricingSnapshot snapshot : snapshots) {
      switch (snapshot.source()) {
        case REMOTE -> anyRemote = true;
        case FALLBACK -> anyFallback = true;
        case OVERRIDE -> anyOverride = true;
        default -> {
          // exhaustive — switch is over PricingSource enum
        }
      }
    }
    if (anyRemote && !anyFallback && !anyOverride) {
      return "litellm";
    }
    if (anyFallback && !anyRemote && !anyOverride) {
      return "fallback";
    }
    if (anyOverride && !anyRemote && !anyFallback) {
      return "mixed";
    }
    return "mixed";
  }

  private static PricingModelResponse toModel(PricingSnapshot snapshot) {
    return new PricingModelResponse(
        snapshot.provider().configKey(),
        snapshot.model(),
        snapshot.pricing().inputTokenPricePerMillion(),
        snapshot.pricing().outputTokenPricePerMillion(),
        snapshot.source().name(),
        snapshot.fetchedAt(),
        snapshot.externalModelId());
  }
}
