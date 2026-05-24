# Design: Pricing Snapshot Identity (TKM-48)

## Technical Approach

Attach a deterministic, content-derived id (`v1:<sha256hex>`) to every successful `analysis` and `analysis_job` so callers can later answer "are these costs still current?" and TKM-45 can dedupe on `(repoKey, pricingSnapshotId)`. The id is computed by canonicalising the sorted active `PricingSnapshot` list (excluding `externalModelId` and `fetchedAt`) and hashing the result with SHA-256. The hash is captured **once per job** at the entry to `CALCULATING_COSTS` inside a new `PricingSnapshotHandle` value object that is threaded through cost estimation and persistence, making the run immune to a mid-pipeline `PricingRefreshedEvent`.

The change is intentionally additive: no change to the cost formula, no change to `model_pricing`, no new pricing table.

## Architecture Decisions

| Decision | Choice | Rejected | Why |
|---|---|---|---|
| Identity scheme | SHA-256 hex with inline `v1:` prefix in `VARCHAR(80)` | separate version column; flat SHA-256 hex | Inline prefix is self-describing, future-proof, and avoids an extra column. 80 chars = 67 used + headroom. |
| Where to compute | New `PricingSnapshotIdentityService` (application layer); cache co-located with `CompositePricingProvider` and invalidated on `PricingRefreshedEvent` | Method on `PricingProvider` | Keeps the port interface stable; centralises canonicalisation; survives mocking in tests. |
| Capture point in pipeline | Entry of `CALCULATING_COSTS` in `AnalysisJobExecutionService` | At submission; per call to `estimate()` | Submission has no costs to label; per-call risks two ids if a refresh fires mid-stream. Capturing once at `CALCULATING_COSTS` gives job & analysis the same id. |
| Hash inputs | `(configKey, model.lowercase().trim(), inputPerM, outputPerM, source)` | include `externalModelId`, `fetchedAt` | A reseed with identical prices must collapse — that's exactly what TKM-45 needs. |
| API shape | Nested `pricing { snapshotId, primarySource, capturedAt }` | Flat top-level fields | Future-proof for new fields (e.g. `pricesUrl`); omittable as a single block when NULL. |
| Legacy NULL rows | Omit the `pricing` block entirely | `pricing: null`; backfilled placeholder | Backfill would be misleading; omitting reads as "unknown provenance". |
| Index | Single-column b-tree on `analysis(pricing_snapshot_id)` | Compound `(snapshot_id, owner, name)` | TKM-45 will refine; over-indexing now is premature. |

## Data Flow

    submit ──► QUEUED row (no pricing id yet)
                  │
                  ▼ worker pulls
    CALCULATING_COSTS ──► PricingSnapshotIdentityService.capture()
                              │       (read once, cached, immune to refresh)
                              ▼
                       PricingSnapshotHandle(id, primarySource, capturedAt, snapshots)
                              │
                  ┌───────────┴───────────┐
                  ▼                       ▼
       costEstimationService.       jobRepository.markPricing(
           estimate(tokens,             jobId, handle)
           handle)                  (UPDATE analysis_job
                  │                  SET pricing_*)
                  ▼
       SAVING_REPORT ──► persistenceService.save(result, handle)
                              │
                              ▼ analysis row written
                                with same pricing_snapshot_id
                              ▼
                       emitter.success(...) ──► COMPLETED

A `PricingRefreshedEvent` arriving after `capture()` flips the provider cache but the handle held by the worker is unaffected: the same `id` and snapshot list are reused for both `estimate()` and persistence.

## File Changes

| File | Action | Description |
|---|---|---|
| `backend/.../db/migration/V8__pricing_snapshot_identity.sql` | Create | Adds three nullable columns to `analysis` and `analysis_job`; b-tree index on `analysis(pricing_snapshot_id)`. |
| `backend/.../domain/pricing/PricingSnapshotId.java` | Create | Value record wrapping the canonical string. |
| `backend/.../domain/pricing/PricingSnapshotHandle.java` | Create | Immutable record `(id, primarySource, capturedAt, snapshots)`. |
| `backend/.../application/pricing/PricingSnapshotIdentityService.java` | Create | Canonicalise + SHA-256 + cache; listens for `PricingRefreshedEvent`. |
| `backend/.../infrastructure/pricing/CompositePricingProvider.java` | Modify | Wire identity service to its cache invalidation hook (no behavioural change otherwise). |
| `backend/.../application/cost/RepositoryCostEstimationService.java` | Modify | Add overload `estimate(long baseTokens, PricingSnapshotHandle handle)` that iterates the handle's snapshots instead of `pricingProvider.all()`. Old method delegates to the overload for back-compat in tests. |
| `backend/.../application/analyzer/AnalysisJobExecutionService.java` | Modify | At `CALCULATING_COSTS`: capture handle, persist on job row via emitter, pass to estimation and persistence. |
| `backend/.../application/analyzer/AnalysisJobProgressEmitter.java` | Modify | New method `markPricing(jobId, handle)` issuing a `REQUIRES_NEW` UPDATE on the job entity. |
| `backend/.../infrastructure/persistence/analysis/AnalysisEntity.java` | Modify | Add `pricingSnapshotId`, `pricingPrimarySource`, `pricingCapturedAt`. |
| `backend/.../infrastructure/persistence/analysis/jobs/AnalysisJobEntity.java` | Modify | Same three columns + getters/setters. |
| `backend/.../infrastructure/persistence/analysis/JpaAnalysisPersistenceService.java` | Modify | Accept handle (new method overload) and copy fields onto the entity. |
| `backend/.../application/analyzer/RepositoryAnalysisResult.java` | Modify | Optional `pricing` field (carry-through from worker to persistence). |
| `backend/.../domain/job/AnalysisJobSnapshot.java` | Modify | Add nullable `pricing` field (carry-through to status mapper). |
| `backend/.../infrastructure/web/analyzer/RepositoryAnalysisResponse.java` | Modify | Add `PricingMetadata` nested record; `@JsonInclude(NON_NULL)`. |
| `backend/.../infrastructure/web/analyzer/CostBreakdownResponse.java` | Modify | Same `PricingMetadata`. |
| `backend/.../infrastructure/web/analyzer/AnalysisJobStatusResponse.java` | Modify | Same `PricingMetadata`. |
| `backend/.../infrastructure/web/analyzer/{RepositoryAnalysisMapper,CostBreakdownMapper,AnalysisJobResponseMapper}.java` | Modify | Populate `pricing` when present; emit `null` otherwise. |
| `frontend/src/types/api.ts` | Modify | Optional `pricing?: { snapshotId; primarySource; capturedAt }` on `RepositoryAnalysisResponse`, `AnalysisJobStatusResponse`, cost-breakdown type. |
| `frontend/src/pages/DashboardPage.tsx` | Modify | Add a second footer line below `Analysis id` only when `analysis.pricing` is present: `Pricing: <primarySource> · captured <date>`. |
| `docs/API.md`, `docs/ARCHITECTURE.md`, `CLAUDE.md` | Modify | Document the field and the capture point. |

## Interfaces / Contracts

```java
public record PricingSnapshotId(String value) {
  public PricingSnapshotId {
    if (value == null || !value.startsWith("v1:") || value.length() != 67) {
      throw new IllegalArgumentException("invalid pricing snapshot id: " + value);
    }
  }
}

public record PricingSnapshotHandle(
    PricingSnapshotId id,
    PricingSource primarySource,   // OVERRIDE > REMOTE > FALLBACK from the snapshots
    Instant capturedAt,             // wall clock when the worker captured the handle
    List<PricingSnapshot> snapshots // the exact list used for estimation
) {}
```

Canonical input fed to SHA-256 (UTF-8, no trailing newline trimming):

```
configKey \t modelLowerTrim \t inputPerM(scale=6,HALF_UP) \t outputPerM(scale=6,HALF_UP) \t source \n
```

Ordering: ascending by `configKey`, then `model.toLowerCase(Locale.ROOT)`. This is the same comparator already used by `CompositePricingProvider.BY_PROVIDER_THEN_MODEL`. The identity service MUST re-sort defensively rather than trusting upstream order.

Wire shape (NON_NULL):

```json
"pricing": {
  "snapshotId": "v1:<64 hex>",
  "primarySource": "REMOTE",
  "capturedAt": "2026-05-24T18:42:11Z"
}
```

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | `PricingSnapshotIdentityService` deterministic: two builds with identical prices but different `fetchedAt`/`externalModelId` yield the same id | Pure JUnit; assert string equality |
| Unit | Different prices → different id | Pure JUnit |
| Unit | Canonicalisation re-sorts inputs (feed shuffled list, expect stable id) | Pure JUnit |
| Unit | `PricingSnapshotId` rejects bad inputs (no prefix, wrong length, null) | Pure JUnit |
| Unit | Cache invalidation: fire `PricingRefreshedEvent`, prices change, id changes; without event, id stable | `ApplicationEventPublisher` in test |
| Integration | Full job run persists same `pricing_snapshot_id` on `analysis_job` and `analysis` | `@SpringBootTest` with H2 + sync executor |
| Integration | `PricingRefreshedEvent` mid-job does NOT desync job-id vs analysis-id | Override executor to publish event between `markPricing` and `save` |
| Web | `GET /api/analyze/{id}` returns `pricing` block when populated, omits it when NULL | `MockMvc` |
| Web | `GET /api/analyze/jobs/{jobId}` returns `pricing` only after `CALCULATING_COSTS` | `MockMvc` |
| Frontend | Footer line renders only when `analysis.pricing` present | Vitest/RTL on `DashboardPage` |

## Migration / Rollout

`V8__pricing_snapshot_identity.sql`:

```sql
ALTER TABLE analysis
  ADD COLUMN pricing_snapshot_id VARCHAR(80),
  ADD COLUMN pricing_primary_source VARCHAR(64),
  ADD COLUMN pricing_captured_at TIMESTAMPTZ;

ALTER TABLE analysis_job
  ADD COLUMN pricing_snapshot_id VARCHAR(80),
  ADD COLUMN pricing_primary_source VARCHAR(64),
  ADD COLUMN pricing_captured_at TIMESTAMPTZ;

CREATE INDEX idx_analysis_pricing_snapshot_id
  ON analysis (pricing_snapshot_id);
```

No backfill. Legacy rows stay NULL — mappers omit the `pricing` block in the response. Rollout is single-deploy; clients tolerate the new optional field. Rollback path is defined in the proposal (`V9__drop_pricing_snapshot_id.sql`).

## Open Questions

- [ ] None blocking. Compound index `(pricing_snapshot_id, owner_name, repository_name)` deferred to TKM-45 design.
