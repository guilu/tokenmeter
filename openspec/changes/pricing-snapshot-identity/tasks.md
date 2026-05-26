# Tasks: Pricing Snapshot Identity (TKM-48)

## Phase 1: Foundation (DB + Domain value objects)

- [x] 1.1 Create Flyway migration `backend/src/main/resources/db/migration/V8__pricing_snapshot_identity.sql` adding nullable `pricing_snapshot_id VARCHAR(80)`, `pricing_primary_source VARCHAR(64)`, `pricing_captured_at TIMESTAMPTZ` to `analysis` and `analysis_job`, plus `idx_analysis_pricing_snapshot_id` b-tree index. Acceptance: Flyway boots clean on H2 + Postgres.
- [x] 1.2 Create `backend/src/main/java/dev/diegobarrioh/tokenmeter/domain/pricing/PricingSnapshotId.java` (record wrapping canonical string; validate `v1:` prefix + 67-char length; reject null/wrong length). Acceptance: `PricingSnapshotIdTest` covers bad inputs (no prefix, short, null) and a valid `v1:` + 64-hex value.
- [x] 1.3 Create `backend/src/main/java/dev/diegobarrioh/tokenmeter/domain/pricing/PricingSnapshotHandle.java` (immutable record `id`, `primarySource`, `capturedAt`, `snapshots`; defensive `List.copyOf`).

## Phase 2: Identity Service + Provider Wiring

- [x] 2.1 Create `backend/src/main/java/dev/diegobarrioh/tokenmeter/application/pricing/PricingSnapshotIdentityService.java`: canonicalise sorted snapshot list per design (configKey \t modelLowerTrim \t setScale(6,HALF_UP) prices \t source \n), SHA-256, prepend `v1:`. Cache id; `@EventListener(PricingRefreshedEvent.class)` invalidates. Expose `capture()` returning `PricingSnapshotHandle`. Acceptance: `PricingSnapshotIdentityServiceTest` covers determinism, shuffled-input stability, price/source change → new id, `fetchedAt`/`externalModelId` excluded, refresh invalidates cache.
- [x] 2.2 Modify `infrastructure/pricing/CompositePricingProvider.java` only if needed to expose deterministic sorted list to identity service (no behavioural change). Acceptance: existing pricing tests still pass.

## Phase 3: Cost Estimation + Job Pipeline

- [x] 3.1 Modify `application/cost/RepositoryCostEstimationService.java`: add overload `estimate(long baseTokens, PricingSnapshotHandle handle)` iterating `handle.snapshots()`; existing `estimate(long)` delegates by capturing handle from identity service. Acceptance: unit test asserts handle-driven path ignores mid-call provider mutations.
- [x] 3.2 Modify `application/analyzer/RepositoryAnalysisResult.java` to carry an optional `PricingSnapshotHandle pricing` field (nullable).
- [x] 3.3 Modify `domain/job/AnalysisJobSnapshot.java` to add nullable `pricing` carrier (id, primarySource, capturedAt) for status mapper.
- [x] 3.4 Modify `application/analyzer/AnalysisJobProgressEmitter.java` (and JPA impl) with `markPricing(jobId, handle)` issuing `@Transactional(REQUIRES_NEW)` UPDATE on `analysis_job` pricing columns.
- [x] 3.5 Modify `application/analyzer/AnalysisJobExecutionService.runJob`: at start of `CALCULATING_COSTS`, call `identityService.capture()`, `emitter.markPricing(jobId, handle)`, pass handle to `estimate(...)` and downstream `RepositoryAnalysisResult`. Acceptance: integration test asserts job/analysis ids match and mid-job refresh does not desync.

## Phase 4: Persistence

- [x] 4.1 Modify `infrastructure/persistence/analysis/AnalysisEntity.java` adding `pricingSnapshotId`, `pricingPrimarySource`, `pricingCapturedAt` columns + getters/setters.
- [x] 4.2 Modify `infrastructure/persistence/analysis/jobs/AnalysisJobEntity.java` adding same three columns + accessors; ensure repository read maps them onto `AnalysisJobSnapshot.pricing`.
- [x] 4.3 Modify `infrastructure/persistence/analysis/JpaAnalysisPersistenceService.save(...)` to accept handle and copy pricing fields onto `AnalysisEntity` before persist.

## Phase 5: Web API

- [x] 5.1 Create nested `PricingMetadata` record (`snapshotId`, `primarySource`, `capturedAt`) and apply `@JsonInclude(JsonInclude.Include.NON_NULL)` on `infrastructure/web/analyzer/RepositoryAnalysisResponse.java`, `CostBreakdownResponse.java`, `AnalysisJobStatusResponse.java`. Acceptance: MockMvc test asserts `pricing` key omitted when null, present when set.
- [x] 5.2 Update mappers `RepositoryAnalysisMapper`, `CostBreakdownMapper`, `AnalysisJobResponseMapper` to populate `pricing` from entity/snapshot when non-null.

## Phase 6: Frontend

- [x] 6.1 Add optional `pricing?: { snapshotId; primarySource; capturedAt }` to `frontend/src/types/api.ts` on `RepositoryAnalysisResponse`, `AnalysisJobStatusResponse`, cost-breakdown type.
- [x] 6.2 Modify `frontend/src/pages/DashboardPage.tsx` to render `Pricing: <primarySource> · captured <date>` line under `Analysis id` using the same date helper; render only when `analysis.pricing` is defined. Acceptance: Vitest/RTL test asserts footer presence/absence per scenario.

## Phase 7: Tests

- [x] 7.1 Unit: `PricingSnapshotIdentityServiceTest` covers determinism, shuffle, price/source change, exclusion of `fetchedAt`/`externalModelId`, refresh invalidation (per pricing spec scenarios).
- [x] 7.2 Unit: `PricingSnapshotIdTest` covers prefix/length validation.
- [x] 7.3 Unit: `RepositoryCostEstimationServiceTest` adds case for handle-driven invocation (mid-call refresh ignored).
- [x] 7.4 Integration: `@SpringBootTest` job run asserts `analysis.pricing_snapshot_id == analysis_job.pricing_snapshot_id` and pre-cost failure leaves NULL.
- [x] 7.5 Web: `MockMvc` tests for `/api/analyze/{id}`, `/cost-breakdown`, `/jobs/{jobId}` asserting block present/omitted.
- [x] 7.6 Frontend: Vitest/RTL test on `DashboardPage` footer rendering.

## Phase 8: Documentation

- [x] 8.1 Update `docs/API.md` documenting nested `pricing` block on analysis/cost-breakdown/job responses.
- [x] 8.2 Update `docs/ARCHITECTURE.md` describing capture point at `CALCULATING_COSTS` and identity canonicalisation.
- [x] 8.3 Update `CLAUDE.md` flow section to mention `pricing_snapshot_id` propagation.

## Phase 9: Quality gate

- [x] 9.1 Run `cd backend && ./gradlew spotlessApply check`.
- [x] 9.2 Run `cd frontend && npm run lint && npm run build`.
- [x] 9.3 Smoke: PR checks passed for #30 (`Backend`, `Frontend`, `SonarCloud`) as the repository smoke path.
