# Exploration: pricing-snapshot-identity (TKM-48)

## Problem framing

TokenMeter quotes a USD floor for "regenerating" a repository with each tracked AI model. The numbers an analysis reports depend entirely on the pricing set active at the moment of computation: a YAML fallback row, a LiteLLM-refreshed REMOTE row, or an in-memory OVERRIDE. Today neither the persisted analysis nor the in-flight job records **which** pricing set produced the numbers; we only persist the resulting `cost_estimates` rows. A user (or future deduplication code) cannot answer "were these costs computed against the same prices as the ones I'm looking at now?" without re-running the analysis.

TKM-48 introduces a stable, deterministic **pricing snapshot identity** that:

1. Is computed from the full active `(provider, model, inputPrice, outputPrice, source)` set, so two semantically identical snapshots yield the same id and two distinct snapshots yield different ids.
2. Is attached to every `analysis_job` (persisted as soon as the worker picks the job up) and every `analysis` row (persisted alongside the cost estimates).
3. Is exposed via the analysis read API, the cost-breakdown endpoint and the job status endpoint so the frontend can render a small "Prices used: …" footer/badge.
4. Becomes the half of the deduplication key that TKM-45 needs (`repoKey + pricingSnapshotId`).

The change is intentionally *additive*: the existing `model_pricing` table, the `PricingProvider` contract and the cost formula stay unchanged. We are only labelling an existing snapshot, not rewriting it.

## Current state (relevant fragments)

- **Pricing in memory** (`infrastructure/pricing/CompositePricingProvider.java`): caches the merged `List<PricingSnapshot>` until a `PricingRefreshedEvent` invalidates it. `PricingSnapshot(pricing, source, fetchedAt, externalModelId)` is the natural unit we'd hash, but it currently has no identity beyond `(provider, model)`.
- **Pricing on disk** (`V5__model_pricing_snapshot.sql` → `ModelPricingEntity`): one row per `(provider, model)`, with `source` (`OVERRIDE | REMOTE | FALLBACK`), `fetchedAt`, prices, and optional `external_model_id`. No "snapshot id" column. Refreshes call `JpaPricingSnapshotStore.replaceRemote(...)` which deletes-and-reinserts REMOTE+FALLBACK rows.
- **Cost computation** (`application/cost/RepositoryCostEstimationService.java`): iterates `PricingProvider.all()` and emits `N × 3` `ModelCostEstimate` records. No reference back to a snapshot id.
- **Analysis persistence** (`AnalysisEntity` / `V2__analysis_persistence.sql`): no pricing-snapshot column.
- **Job persistence** (`AnalysisJobEntity` / `V6__analysis_job.sql`): no pricing-snapshot column. `AnalysisJobExecutionService.runJob` currently calls `costEstimationService.estimate(totalTokens)` against whatever snapshot `CompositePricingProvider` returns at that instant; if a `PricingRefreshedEvent` arrives mid-pipeline, the next call will see a fresh snapshot.
- **HTTP surface**:
  - `GET /api/pricing` (`PricingResponse`) already returns `primarySource`, `lastRefreshedAt`, per-model `source`/`fetchedAt`. No top-level snapshot id.
  - `GET /api/analyze/{id}` (`RepositoryAnalysisResponse`) and `GET /api/analyze/{id}/cost-breakdown` (`CostBreakdownResponse`) return cost estimates but no provenance.
  - `GET /api/analyze/jobs/{jobId}` (`AnalysisJobStatusResponse`) has no pricing reference.
- **Frontend** (`frontend/src/types/api.ts`, `pages/DashboardPage.tsx`): consumes the analysis/job responses but never surfaces pricing provenance on the results screen.

## Affected systems

- **DB** — `V8__analysis_pricing_snapshot.sql` (new): add `pricing_snapshot_id VARCHAR(64)` (or `CHAR(64)` if we go SHA-256 hex) to `analysis` and `analysis_job`. Nullable for backfill; populated for every new row. Optionally index on `analysis(pricing_snapshot_id)` to support TKM-45's dedup lookup.
- **Domain** — new value object `PricingSnapshotId` (record wrapping the canonical string). Possibly a `PricingSnapshotDigest` helper to centralise the canonicalisation.
- **Application** — `PricingProvider` (or a sibling `PricingSnapshotIdentityService`) needs a way to expose "the id of the snapshot you'd return *right now*", computed once per refresh. `AnalysisJobExecutionService.runJob` must (a) capture the id when it starts costing, (b) pass it into `RepositoryCostEstimationService.estimate(...)` so the same snapshot is read consistently, (c) persist it on the job and on the resulting analysis.
- **Infrastructure** —
  - `AnalysisEntity` + `AnalysisJobEntity` get a new column/getter/setter.
  - `JpaAnalysisPersistenceService` + `JpaAnalysisJobProgressEmitter` (or wherever the job is first persisted) propagate the id.
  - `CompositePricingProvider` either exposes the id alongside the cached snapshot list or delegates to a `PricingSnapshotIdentityService` that recomputes on `PricingRefreshedEvent`.
- **Web** —
  - `AnalysisJobStatusResponse` gains an optional `pricing` object (`snapshotId`, `lastRefreshedAt`, `primarySource`).
  - `RepositoryAnalysisResponse` and `CostBreakdownResponse` gain a `pricing` object with `snapshotId`, `capturedAt` (== `analysis.created_at` for old rows or `pricing.fetchedAtMax` for new ones), `primarySource`.
- **Frontend** — minimal: render the id (short, e.g. first 12 chars) plus `primarySource` and a tooltip with `fetchedAt`. No screen redesign.
- **Docs** — `docs/API.md`, `docs/ARCHITECTURE.md`, `CLAUDE.md` flow section.

## Existing assets we can reuse

- `PricingSnapshot` record already has every field we need to hash (provider, model, prices, source, fetchedAt, externalModelId). We do **not** need a new persistence table; the identity is derivable.
- `PricingRefreshedEvent` is the natural invalidation hook for any cache that maps "current snapshot → id".
- `JpaPricingSnapshotStore.findAll()` already returns the deterministic, sorted list `findAllByOrderByProviderAscModelAsc` — that ordering is exactly what we'd canonicalise.
- `CompositePricingProvider` already caches the merged snapshot list per refresh; co-locating the id with that cache is cheap.
- The existing `model_pricing` table (V5) gives us the source-of-truth rows. No new table needed if we go the **content-hash** route. If we go the **stored snapshot** route (Option B below) we'd add a `pricing_snapshot` table.
- The cost-breakdown response already exposes the per-row pricing (input/output per million) via `CostBreakdownPricingResponse`. We can extend, not replace.

## Approaches

### Option A — Content hash over the active snapshot (recommended)

Compute `pricingSnapshotId` as a SHA-256 (hex, 64 chars) over a canonical UTF-8 serialisation of the sorted list of `(provider.configKey(), model, inputPricePerMillion, outputPricePerMillion, source)` tuples (prices serialised with a fixed scale, e.g. `setScale(6, HALF_UP).toPlainString()` so trailing zeros never affect the hash). Include `externalModelId` only if we decide that two snapshots with the same prices but different upstream ids are semantically different (probably **no** — exclude it so YAML/REMOTE same-price scenarios collapse).

- The id is purely derived; no extra table.
- Cache the id alongside `CompositePricingProvider.snapshots()` and invalidate on `PricingRefreshedEvent` (the cache already does this for the snapshot list).
- Persist the id on `analysis_job` (set the first time the worker reads the snapshot, before calling `estimate(...)`) and on `analysis` (passed through `JpaAnalysisPersistenceService.save`).
- Two analyses that hit the same FALLBACK seed produce the same id; a refresh that materially changes any price produces a new id. **Crucially, a `fetchedAt`-only change (e.g. re-seed of FALLBACK with identical prices) does NOT change the id** — this is exactly the property TKM-45 needs to avoid spurious cache misses.

Pros:
- Zero migration weight beyond two nullable columns.
- Deterministic, stable across restarts, identical inputs → identical id even after a process restart that re-seeds FALLBACK rows with a new `fetchedAt`.
- Cheap to compute (≤ 50 rows × small string).
- Backfill is trivial: leave existing rows `NULL`, new rows populate.

Cons:
- The id alone doesn't tell you *what* the prices were; the API needs to expose `primarySource` and `fetchedAt` alongside the id to make it human-meaningful. (Already planned.)
- If we ever decide that `fetchedAt` is part of the identity (e.g. for audit), we'd need a new id scheme.
- Hash collisions are a non-issue at this cardinality but the column has to be wide enough (64 chars for SHA-256 hex).

Effort: **Low** (one Flyway migration, one new domain record, one service tweak, mappers + tests). Roughly 1.5 sessions of work.

### Option B — Persisted snapshot table with a surrogate id

Introduce `pricing_snapshot(id PK, content_hash UNIQUE, captured_at, primary_source)` plus `pricing_snapshot_model(snapshot_id FK, provider, model, input_price, output_price, source, fetched_at, external_model_id)`. Each refresh inserts a new snapshot row (or reuses one if the hash already exists). `analysis_job.pricing_snapshot_id` and `analysis.pricing_snapshot_id` are FKs.

Pros:
- Full audit: you can reconstruct exactly which prices any historical analysis used, even if `pricing.yaml` was later edited or LiteLLM's upstream changed.
- Snapshot id is a short surrogate (BIGSERIAL or UUID) instead of a 64-char hash.

Cons:
- Two new tables and a non-trivial write path on every refresh (`PricingSnapshotStorePort` would need a sibling).
- Storage scales with refresh frequency. With hourly refreshes and ≥ 17 models we'd write ≥ 408 rows/day in the worst case (mitigated by the content-hash uniqueness, but still).
- The "we can reconstruct exact historical prices" benefit isn't in the TKM-48 acceptance criteria — that's overkill for what the ticket asks.
- Heavier to test and roll back.

Effort: **Medium–High** (two migrations, FK constraints, a new repository, a refresh-time write path, much more test surface).

### Option C — Use `fetchedAt-max` as the identity

Use the maximum `fetchedAt` across the active snapshot as the id (an ISO-8601 instant). Cheap; already exposed.

Pros: trivial to implement.

Cons:
- **Not stable**: if the cold-start seed re-runs (e.g. fresh DB), every row gets a new `fetchedAt`, so the "id" changes even though prices are identical. Breaks TKM-45's dedup property.
- Two simultaneously-refreshed runs might differ by milliseconds and falsely look "different".
- Doesn't compose: an OVERRIDE layer with no `fetchedAt` would be invisible.

Rejected.

## Recommendation

**Option A (content hash).** It maps cleanly onto the existing `PricingSnapshot` value object, doesn't require new tables, and produces the exact invariant TKM-45 needs (same prices → same id; different prices → different id). The audit story (Option B) is not in scope for TKM-48, and we don't want to pay the schema cost until a future change explicitly requires it.

Sketch of the canonical serialisation (to be locked in `sdd-design`):

```
sha256(
  for each snapshot in sortAscByProviderConfigKeyThenModel(active):
    provider.configKey() + "\t" +
    model.toLowerCase(Locale.ROOT).trim() + "\t" +
    inputPricePerMillion.setScale(6, HALF_UP).toPlainString() + "\t" +
    outputPricePerMillion.setScale(6, HALF_UP).toPlainString() + "\t" +
    source.name() + "\n"
)
```

`externalModelId` and `fetchedAt` are deliberately excluded so re-seeding doesn't perturb identity.

## Open questions / risks

- **Backfill policy.** Existing `analysis` and `analysis_job` rows have no snapshot id. Options: (a) leave `NULL` (deduplication just can't match historical rows — acceptable), (b) compute and write the *current* id during the migration (misleading: old analyses might not have used today's prices), (c) compute the id from the persisted `cost_estimates` rows (only possible if we add a derivation). **Lean toward (a)**.
- **Where to capture the id during job execution.** It MUST be captured *once* and passed through `estimate(...)` → persisted on `analysis_job` and `analysis`. Risk: a `PricingRefreshedEvent` mid-pipeline would otherwise produce a job whose id mismatches the analysis. Fix: snapshot the list (and its id) at the top of the cost-estimation phase and feed it through. May require an overload `RepositoryCostEstimationService.estimate(long, List<PricingSnapshot>)` or a small `PricingSnapshotHandle` parameter object.
- **Does the id need a version prefix?** E.g. `v1:<hex>`. Useful future-proofing if we ever change the canonicalisation. **Probably yes**, propose: store as `v1:` + 64-hex (67 chars). Column would be `VARCHAR(80)`.
- **Job snapshot identity vs analysis snapshot identity.** They should be identical for any successful run, but a job that fails before cost estimation may have no snapshot id. Acceptable — column stays nullable on `analysis_job`.
- **Override layer ordering determinism.** `OverridesPricingLoader.snapshots()` and `JpaPricingSnapshotStore.findAll()` both emit deterministic order; `CompositePricingProvider` merges via `LinkedHashMap`. Verify with a test that two builds of the merged list yield byte-identical canonical input.
- **TKM-45 contract.** TKM-45 will key on `(repoKey, pricingSnapshotId)`. We should make sure the column is indexable (`VARCHAR` with adequate length is fine in Postgres). A compound index on `analysis(pricing_snapshot_id, owner_name, repository_name)` may help — defer to TKM-45's design phase.
- **API exposure shape.** Whether to nest the new fields under a `pricing` object (recommended for forward-compat) or to flatten as top-level `pricingSnapshotId`. Lean toward nested.
- **Tests.** Need a unit test that asserts the hash is stable across two `PricingProvider` builds with identical content but different `fetchedAt`. Need an integration test that asserts `analysis.pricing_snapshot_id == analysis_job.pricing_snapshot_id` for any successful run.

## Ready for proposal

Yes. Recommend proceeding with **Option A**. The proposal phase should pin down: (1) exact canonicalisation format and version prefix; (2) where to insert the "capture id" hook in `AnalysisJobExecutionService` and which method on `PricingProvider` exposes it; (3) backfill policy (lean: leave NULL); (4) API response shape (nested `pricing` object); (5) the new Flyway migration `V8__analysis_pricing_snapshot.sql`.
