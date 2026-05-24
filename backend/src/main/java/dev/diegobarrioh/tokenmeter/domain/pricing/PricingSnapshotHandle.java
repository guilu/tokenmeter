package dev.diegobarrioh.tokenmeter.domain.pricing;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, frozen view of the active pricing snapshot used by an in-flight cost estimation.
 *
 * <p>Captured once per job at the entry of {@code CALCULATING_COSTS} so that a mid-pipeline {@code
 * PricingRefreshedEvent} can never desync the identifier persisted on the originating job from the
 * identifier persisted on the resulting analysis.
 *
 * @param id deterministic identifier derived from {@link #snapshots()}
 * @param primarySource winning layer at the time of capture (OVERRIDE &gt; REMOTE &gt; FALLBACK).
 *     Computed across the snapshot list, not per-row.
 * @param capturedAt wall-clock instant when the worker captured this handle
 * @param snapshots the exact, ordered snapshot list used for estimation; defensively copied
 */
public record PricingSnapshotHandle(
    PricingSnapshotId id,
    PricingSource primarySource,
    Instant capturedAt,
    List<PricingSnapshot> snapshots) {

  public PricingSnapshotHandle {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(primarySource, "primarySource is required");
    Objects.requireNonNull(capturedAt, "capturedAt is required");
    Objects.requireNonNull(snapshots, "snapshots is required");
    snapshots = List.copyOf(snapshots);
  }
}
