# Proposal: Pricing Snapshot Identity (TKM-48)

## Intent

Every `analysis` is computed against a specific active pricing set (YAML fallback, REMOTE refresh, or OVERRIDE). Today no row records *which* set was used, so we cannot answer "are these costs still current?" nor build the dedup key TKM-45 needs (`repoKey + pricingSnapshotId`). Goal: attach a deterministic, content-derived id to every analysis and job, expose it via the API and the UI.

## Scope

### In Scope
- New domain value object `PricingSnapshotId` (`v1:` + SHA-256 hex; `VARCHAR(80)`).
- Identity computed by canonicalising the active sorted `(provider.configKey(), model, inputPricePerMillion, outputPricePerMillion, source)` tuples; `externalModelId` and `fetchedAt` excluded.
- Capture hook in `AnalysisJobExecutionService.runJob` at start of `CALCULATING_COSTS`: read the snapshot list + id once, pass both to `RepositoryCostEstimationService.estimate(...)`, persist on job and analysis.
- Flyway `V8__analysis_pricing_snapshot.sql`: nullable `pricing_snapshot_id VARCHAR(80)` on `analysis` and `analysis_job`. Plain b-tree index on `analysis(pricing_snapshot_id)` to anticipate TKM-45.
- Expose nested `pricing` object on `RepositoryAnalysisResponse`, `CostBreakdownResponse`, `AnalysisJobStatusResponse` with `snapshotId`, `primarySource`, `capturedAt`.
- Frontend: short id (first 12 chars of hex) + `primarySource` badge with `capturedAt` tooltip on the results page.
- Docs: `docs/API.md`, `docs/ARCHITECTURE.md`, CLAUDE.md flow section.

### Out of Scope
- Persisted historical snapshot table (Option B). Deferred — no reconstruction requirement in TKM-48.
- Cache reuse logic — that is TKM-45.
- Backfilling existing rows (stays `NULL`).
- Changing the cost formula or `model_pricing` schema.

## Approach

Content-hash (Exploration Option A). `CompositePricingProvider` caches the merged snapshot list per refresh; co-locate `PricingSnapshotId` next to that cache and invalidate on `PricingRefreshedEvent`. A new `PricingSnapshotIdentityService` (or method on `PricingProvider`) returns a `PricingSnapshotHandle(List<PricingSnapshot>, PricingSnapshotId, Instant capturedAt)`. The worker reads the handle once at the cost phase, threads it through estimation and persistence so a mid-job refresh cannot desync job-id from analysis-id. Canonical serialisation:

```
v1:sha256( for each sorted snapshot:
  configKey + \t + model.toLowerCase().trim() + \t +
  inputPerM.setScale(6,HALF_UP).toPlainString() + \t +
  outputPerM.setScale(6,HALF_UP).toPlainString() + \t +
  source.name() + \n )
```

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `db/migration/V8__analysis_pricing_snapshot.sql` | New | Two nullable cols + index |
| `domain/pricing/PricingSnapshotId.java` | New | Value object + factory |
| `application/pricing/PricingSnapshotIdentityService.java` | New | Canonicalise + hash; cached |
| `application/cost/RepositoryCostEstimationService.java` | Modified | Overload taking `PricingSnapshotHandle` |
| `application/job/AnalysisJobExecutionService.java` | Modified | Capture handle, propagate id |
| `infrastructure/persistence/analysis/AnalysisEntity` + jobs entity | Modified | New column + getter |
| `infrastructure/web/.../*Response` + mappers | Modified | Nested `pricing` object |
| `frontend/src/types/api.ts` + results page | Modified | Render id + source badge |
| `docs/API.md`, `docs/ARCHITECTURE.md`, `CLAUDE.md` | Modified | Document identity + flow |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Mid-job `PricingRefreshedEvent` desyncs job vs analysis id | Med | Capture handle once at `CALCULATING_COSTS`; thread through estimate + persistence |
| Non-deterministic merge order in `CompositePricingProvider` | Low | Unit test: two builds of merged list produce byte-identical canonical input |
| Future canonicalisation change breaks ids | Low | `v1:` prefix; new prefix on schema change |
| Column too narrow for future hash | Low | `VARCHAR(80)` accommodates v1 (67) + headroom |
| Old rows lacking id confuse TKM-45 | Low | Backfill = NULL; TKM-45 treats NULL as cache miss |

## Rollback Plan

1. Revert response DTO changes (clients tolerate missing `pricing` block).
2. Stop populating the id in `AnalysisJobExecutionService` (no-op writes).
3. New Flyway migration `V9__drop_pricing_snapshot_id.sql` drops the columns + index. The V8 migration itself is never edited. No data loss — `cost_estimates` rows are untouched.

## Dependencies

- None upstream. Blocks TKM-45 (analysis cache reuse).

## Success Criteria

- [ ] New `analysis` and `analysis_job` rows persist a non-null `pricing_snapshot_id` for every successful run.
- [ ] For a given pricing set, identical re-seeds (different `fetchedAt`, same prices) yield identical ids; any price/source change yields a different id.
- [ ] `analysis.pricing_snapshot_id == analysis_job.pricing_snapshot_id` for every successful job (integration test).
- [ ] `GET /api/analyze/{id}`, `/cost-breakdown`, `/jobs/{jobId}` expose the nested `pricing` object.
- [ ] Frontend results page shows the short id + `primarySource` badge.
- [ ] Docs updated (`API.md`, `ARCHITECTURE.md`, `CLAUDE.md`).

## Open Decisions (need sign-off before sdd-spec)

1. **Version prefix.** Confirm `v1:` literal stored in column (vs. separate `pricing_snapshot_id_version` column). Proposal: inline prefix.
2. **API shape.** Confirm nested `pricing { snapshotId, primarySource, capturedAt }` object (vs. flat `pricingSnapshotId`). Proposal: nested.
3. **`capturedAt` semantics.** For new rows: instant the worker captured the handle. For old rows (NULL id): omit the whole `pricing` block. Proposal: omit.
4. **Index shape.** Plain `CREATE INDEX ON analysis(pricing_snapshot_id)` now; defer compound `(pricing_snapshot_id, owner_name, repository_name)` to TKM-45. Proposal: defer.
5. **Frontend placement.** Footer of the cost table vs. badge next to the repo header. Proposal: footer next to "Generated at …".
