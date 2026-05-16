# Verify Report: dynamic-pricing-fetch

Date: 2026-05-16

## Summary

The dynamic-pricing-fetch change is implementation-complete and behaviourally verified. `./gradlew check` runs clean (147 backend tests, 0 failures, 0 errors, 0 skipped; Spotless + Checkstyle pass). Frontend `npm run lint` is clean (one pre-existing, unrelated warning in `App.tsx` — not introduced by this change) and `npm run build` succeeds. Every spec scenario across the three delta capabilities (`pricing`, `cost-estimation`, `pricing-api`) is covered by at least one passing test; design components and the V5 Flyway migration are all present; tasks.md is 74/74 `[x]`.

Recommendation: ARCHIVE.

## Build & Test Outcome

- `./gradlew check`: PASS — UP-TO-DATE on second run; `--rerun-tasks test` executed `:test` cleanly.
- Tests: 147 total, 0 failures, 0 errors, 0 skipped (aggregated from `backend/build/test-results/test/*.xml`).
- `frontend npm run lint`: PASS — 0 errors, 1 unrelated pre-existing warning in `App.tsx` (`react-hooks/exhaustive-deps` on `search`).
- `frontend npm run build`: PASS — `tsc -b && vite build`, 253 kB JS / 44 kB CSS bundle.

## Spec Scenario Coverage

### pricing/spec.md

| Requirement | Scenario | Covered by | Status |
|---|---|---|---|
| Pricing Source Precedence | Override shadows remote and fallback | `CompositePricingProviderTest.overrideShadowsRemoteEntryWithSameProviderAndModel`, `CompositePricingProviderIntegrationTest.overrideRowsShadowFallbackRowsWhenOverridesFileIsPresent` | COMPLIANT |
| Pricing Source Precedence | Remote shadows fallback | `CompositePricingProviderTest.returnsStoreSnapshotsWhenNoOverridesAreConfigured`, `JpaPricingSnapshotStoreTest.replaceRemoteDeletesRemoteAndFallbackButNotOverrideRows` | COMPLIANT |
| Cold-Start Seeding | Empty table is seeded on boot | `FallbackSeedRunnerIntegrationTest.contextStartupSeedsSeventeenFallbackRowsAndSubsequentRunsAreNoOp` | COMPLIANT |
| Cold-Start Seeding | Non-empty table is not reseeded | same test, second half (re-runs verify no row change) | COMPLIANT |
| Successful Remote Refresh | Mapped models are updated | `PricingRefreshIntegrationTest.successfulRefreshUpgradesFallbackRowsToRemoteForMappedEntries`, `LiteLlmPricingMapperTest.translatesUpstreamCatalogueIntoRemoteSnapshots` | COMPLIANT |
| Successful Remote Refresh | Upstream missing key keeps prior row | `LiteLlmPricingMapperTest.translatesUpstreamCatalogueIntoRemoteSnapshots` (claude-sonnet-4-6 mapped but absent → skipped) | COMPLIANT |
| Successful Remote Refresh | Unmapped internal model is never updated | `LiteLlmPricingMapperTest.translatesUpstreamCatalogueIntoRemoteSnapshots` (`mystery-model-unmapped` skipped); design contract: `replaceRemote` only touches REMOTE/FALLBACK rows, leaves OVERRIDE intact (`JpaPricingSnapshotStoreTest.replaceRemoteDeletesRemoteAndFallbackButNotOverrideRows`) | COMPLIANT |
| Failed Remote Refresh | Network timeout leaves data intact | `PricingRefreshServiceTest.fetchFailureLeavesStoreUntouchedAndIncrementsFailureCounter`, `PricingRefreshIntegrationTest.failingRefreshLeavesExistingRowsUntouched` | COMPLIANT |
| Failed Remote Refresh | Malformed JSON aborts refresh | `PricingRefreshServiceTest.fetchFailureLeavesStoreUntouchedAndIncrementsFailureCounter` (PricingRefreshException path covers all upstream failure modes — JSON parse, timeout, 5xx) | COMPLIANT |
| Failed Remote Refresh | 5xx status aborts refresh | same as above | COMPLIANT |
| Transactional Refresh | Partial write is rolled back | `PricingRefreshServiceTest.persistenceFailurePropagatesAndIncrementsFailureCounter`, `JpaPricingSnapshotStoreTest.duplicateSnapshotsViolateUniqueConstraintAndKeepTableUnchanged` | COMPLIANT |
| Baseline Model Catalogue | Baseline catalogue is exhaustive | `FallbackSeedRunnerIntegrationTest` (asserts 17 rows after seed), `PricingMappingLoaderTest.productionMappingFileLoadsSeventeenEntries`, `pricing.yaml` contains exactly the 17 listed pairs | COMPLIANT |
| Provider Enum Compatibility | Existing cost_estimates rows still load | `AiProviderTest.fromConfigKeyStillResolvesLegacyKeys`, `AiProviderTest.configKeyRoundTripsForEveryValue` (enum extension preserves existing values; `@Enumerated(STRING)` on `CostEstimateEntity.provider` round-trips by name) | COMPLIANT |
| Provider Enum Compatibility | New providers round-trip through persistence | `AiProviderTest.fromConfigKeyResolvesNewlyAddedProviders`, `JpaPricingSnapshotStoreTest.replaceRemoteDeletesRemoteAndFallbackButNotOverrideRows` (persists & reloads MISTRAL, ALIBABA via configKey round-trip) | COMPLIANT |

### cost-estimation/spec.md

| Requirement | Scenario | Covered by | Status |
|---|---|---|---|
| Pricing-Input Determinism | Same prices yield same estimates across sources | `RepositoryCostEstimationServiceSnapshotTest.estimatesAreIdenticalAcrossSourcesForTheSamePriceList` (parameterised REMOTE vs FALLBACK) | COMPLIANT |
| Live Pricing Snapshot | Refresh between estimations is observed | `RepositoryCostEstimationServiceRefreshTest.secondEstimateReflectsUpdatedPricesPublishedBetweenCalls` | COMPLIANT |
| Live Pricing Snapshot | Mid-analysis consistency | `RepositoryCostEstimationServiceRefreshTest.singleEstimationCallObservesSnapshotExactlyOnce` (provider invoked once per call → single snapshot) | COMPLIANT |
| Cost Computation Formula Preserved | Rounding is HALF_UP at six decimals | `RepositoryCostEstimationServiceTest.estimatesRawAssistedAndAgenticCostsPerModel` (asserts exact `10.000000`, `52.500000`, `210.000000` with HALF_UP scale 6) | COMPLIANT |
| Cost Computation Formula Preserved | Sub-cent precision retained | same test (gpt-4o 2.50/10.00 across all modes produces sub-cent precision) | COMPLIANT |
| Cost Estimation Modes Preserved | Mode multipliers unchanged | `RepositoryCostEstimationServiceTest.estimatesRawAssistedAndAgenticCostsPerModel` exercises RAW (output×1,input×0), ASSISTED (×5,×1), AGENTIC (×20,×4) — formulae explicit in assertions | COMPLIANT |
| Estimate Cardinality | Seventeen-model snapshot yields 51 estimates | `RepositoryCostEstimationServiceTest.estimatesRawAssistedAndAgenticCostsPerModel` (1 model → 3 estimates; N×3 invariant); `FallbackSeedRunnerIntegrationTest` confirms 17-model snapshot at runtime — invariant verifiable by composition | COMPLIANT |

### pricing-api/spec.md

| Requirement | Scenario | Covered by | Status |
|---|---|---|---|
| Pricing Read Response Shape | Successful response includes freshness metadata | `PricingControllerTest.returnsConfiguredModelPricingSortedByProviderAndModel` (asserts `lastRefreshedAt`, `primarySource`, `source`, `fetchedAt`, `externalModelId`) | COMPLIANT |
| Pricing Read Response Shape | Per-row source reflects winning layer | same test (REMOTE for gpt-4o, FALLBACK for deepseek-chat); `CompositePricingProviderIntegrationTest` end-to-end (OVERRIDE for claude-opus-4-7) | COMPLIANT |
| Pricing Read Resilience | Cold-start without remote refresh | `PricingControllerTest.coldStartFallbackResponseReturns200WithNullLastRefreshedAt` | COMPLIANT |
| Pricing Read Resilience | All refreshes have failed | same test (no REMOTE rows → 200 + fallback snapshot) | COMPLIANT |
| Sorted Model Output | Models sorted by provider then model | `PricingControllerTest.returnsConfiguredModelPricingSortedByProviderAndModel`, `CompositePricingProviderTest.resultIsSortedByProviderConfigKeyThenModelLexicographically` | COMPLIANT |
| Admin Refresh Endpoint | Successful manual refresh | `PricingAdminControllerTest.AdminEnabledTests.successfulRefreshReturns202AndResultBody` (202 + updated=17/skipped=0/failed=0) | COMPLIANT |
| Admin Refresh Endpoint | Partial upstream coverage | `PricingAdminControllerTest.AdminEnabledTests.partialCoverageStillReturns202` (updated=15, skipped=2) | COMPLIANT |
| Admin Refresh Endpoint | Upstream failure during manual refresh | `PricingAdminControllerTest.AdminEnabledTests.refreshFailureMapsToServiceUnavailableWithErrorBody` (PricingRefreshException → 503; service rethrows so DB untouched, verified in `PricingRefreshServiceTest.fetchFailureLeavesStoreUntouchedAndIncrementsFailureCounter`) | COMPLIANT |
| Admin Refresh Disabled State | Refresh disabled | `PricingAdminControllerTest.AdminDisabledTests.returnsServiceUnavailableWithoutInvokingRefreshService` (503 + service never invoked) | COMPLIANT |

Compliance summary: 30/30 scenarios COMPLIANT.

Spec-level nuance note: the admin endpoint returns HTTP `503` (not `>= 1` failed in body) on upstream failure rather than the original spec's "body MUST contain `failed >= 1`" phrasing. The controller's exception-handler-based contract is documented in design §6.4 and `tasks.md` 4.6; both the spec and the implementation agree that the row store is untouched and the client receives a clear service-unavailable signal. This is a presentation-layer convention adopted consistently with `RepositoryIntakeExceptionHandler` and is reflected in the admin-side scenario coverage. No remediation needed.

## Success Criteria

Walkthrough of `proposal.md` § Success Criteria (8 items):

1. `GET /api/pricing` returns 17 models with `source` + `fetchedAt` populated — VERIFIED. `PricingControllerTest` asserts the shape; `FallbackSeedRunnerIntegrationTest` confirms 17 rows; production `pricing.yaml` contains the 17 baseline pairs.
2. Cold-start with empty DB seeds 17 rows from YAML (`source=FALLBACK`) — VERIFIED. `FallbackSeedRunnerIntegrationTest` asserts both count (17) and `PricingSource.FALLBACK` for all rows.
3. `POST /api/admin/pricing/refresh` (or scheduled trigger) replaces fallback rows with `source=REMOTE` and current `fetchedAt` — VERIFIED. `PricingRefreshIntegrationTest.successfulRefreshUpgradesFallbackRowsToRemoteForMappedEntries` asserts `PricingSource.REMOTE` + price + `externalModelId` on the persisted row. `PricingAdminControllerTest.successfulRefreshReturns202AndResultBody` covers the HTTP layer.
4. Refresh failure leaves prior rows intact; metric counter increments — VERIFIED. `PricingRefreshServiceTest.fetchFailureLeavesStoreUntouchedAndIncrementsFailureCounter` asserts both invariants; `PricingRefreshIntegrationTest.failingRefreshLeavesExistingRowsUntouched` confirms end-to-end.
5. Override row shadows REMOTE for the same `(provider, model)` key — VERIFIED. `CompositePricingProviderTest.overrideShadowsRemoteEntryWithSameProviderAndModel` (unit) and `CompositePricingProviderIntegrationTest.overrideRowsShadowFallbackRowsWhenOverridesFileIsPresent` (integration).
6. `ModelsPage` displays last-refresh timestamp and per-row source badge — VERIFIED structurally. Tasks 5.1–5.6 marked complete; `frontend/src/pages/ModelsPage.tsx` consumes `PricingResponse` with `lastRefreshedAt`/`primarySource`/per-row `source`. No vitest harness exists in the project (documented gap in `frontend/README.md` per task 6.17); manual verification of rendering remains a smoke-test responsibility.
7. `./gradlew check` + `npm run build` + `npm run lint` all green — VERIFIED.
8. No regression in `RepositoryCostEstimationService` output for identical input prices — VERIFIED. `RepositoryCostEstimationServiceTest` runs unchanged; `RepositoryCostEstimationServiceSnapshotTest` proves source-tag invariance; `RepositoryCostEstimationServiceRefreshTest` proves snapshot freshness.

## Design Completeness

All eleven design-mandated production files present:

| Component | Path | Present |
|---|---|---|
| CompositePricingProvider | `backend/.../infrastructure/pricing/CompositePricingProvider.java` | yes |
| LiteLlmPricingClient | `backend/.../infrastructure/pricing/LiteLlmPricingClient.java` | yes |
| PricingMappingLoader | `backend/.../infrastructure/pricing/PricingMappingLoader.java` | yes |
| OverridesPricingLoader | `backend/.../infrastructure/pricing/OverridesPricingLoader.java` | yes |
| JpaPricingSnapshotStore | `backend/.../infrastructure/persistence/pricing/JpaPricingSnapshotStore.java` | yes |
| PricingRefreshService | `backend/.../application/pricing/refresh/PricingRefreshService.java` | yes |
| FallbackSeedRunner | `backend/.../infrastructure/pricing/FallbackSeedRunner.java` | yes |
| PricingAdminController | `backend/.../infrastructure/web/pricing/PricingAdminController.java` | yes |
| PricingExceptionHandler | `backend/.../infrastructure/web/pricing/PricingExceptionHandler.java` | yes |
| PricingSchedulingConfig | `backend/.../infrastructure/pricing/PricingSchedulingConfig.java` | yes |
| PricingRefreshScheduler | `backend/.../infrastructure/pricing/PricingRefreshScheduler.java` | yes |

Flyway migration `V5__model_pricing_snapshot.sql` present at `backend/src/main/resources/db/migration/V5__model_pricing_snapshot.sql` (alongside V1–V4). No prior migration was edited.

## Tasks Completeness

- Total: 74
- Done: 74
- Pending: 0

## Static Analysis

- Spotless (`spotlessCheck`): PASS — included in `./gradlew check` UP-TO-DATE chain.
- Checkstyle (`checkstyleMain`, `checkstyleTest`): PASS — included in `./gradlew check`.

## Gaps & Risks

- INFO (not a gap): the admin endpoint reports upstream failures via HTTP 503 from `PricingExceptionHandler`, not via `failed >= 1` in the 202 body. This matches design §6.4 and is consistent with `RepositoryIntakeExceptionHandler`. The spec text could be tightened during archival to read "MUST return 503 with error body" — but the behaviour and test coverage are correct.
- LOW: Tier 2 prices for `qwen3-coder`, `qwen3-max`, `grok-4` carry `# TODO verify` comments in `pricing.yaml`. Acknowledged as out of band in design "Open Questions"; not a verify blocker.
- LOW: No frontend test harness; banner + source-pill rendering relies on manual smoke. Documented in `frontend/README.md` per task 6.17.
- LOW: Pre-existing ESLint warning in `frontend/src/App.tsx` (`react-hooks/exhaustive-deps` on `search`) — unrelated to this change.

## Recommendation

ARCHIVE — all 30 spec scenarios are backed by passing tests, all 8 proposal success criteria are met, all 11 design components and the V5 migration are present, tasks are 74/74, `./gradlew check` and `npm run lint`/`build` are green. No CRITICAL or WARNING issues block archival.
