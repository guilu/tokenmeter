package dev.diegobarrioh.tokenmeter.domain.job;

import java.util.Objects;
import java.util.UUID;

/** Opaque identifier for an {@code AnalysisJob}. */
public record AnalysisJobId(UUID value) {

  public AnalysisJobId {
    Objects.requireNonNull(value, "AnalysisJobId.value must not be null");
  }

  /** Generates a random identifier. */
  public static AnalysisJobId random() {
    return new AnalysisJobId(UUID.randomUUID());
  }

  /** Convenience factory from a {@link UUID} (alias of the canonical constructor). */
  public static AnalysisJobId of(UUID value) {
    return new AnalysisJobId(value);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
