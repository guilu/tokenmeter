# Tasks: Dynamic Pricing Fetch

Scope: replace static `pricing.yaml` with a three-layer dynamic pricing pipeline (OVERRIDE > REMOTE > FALLBACK) backed by LiteLLM, persisted in `model_pricing`, refreshed on schedule, surfaced in the UI. See `design.md` and the three delta specs (`pricing`, `cost-estimation`, `pricing-api`).

Cross-phase order: Phase 1 (schema/config skeleton) blocks Phases 2–4. Phase 2 (domain/persistence + seed wiring) blocks Phase 3 (refresh pipeline). Phase 3 blocks Phase 4 (web). Phase 4 blocks Phase 5 (frontend). Phase 6 (tests) runs in parallel with Phases 2–5 but final integration tests require all earlier phases complete. Phase 7 (docs/ops) runs last.

## Phase 1: Database & Config Skeleton

- [x] 1.1 Create Flyway migration `backend/src/main/resources/db/migration/V5__model_pricing_snapshot.sql` with `model_pricing` table, `uq_model_pricing_provider_model`, non-negative checks, and `source IN ('REMOTE','FALLBACK','OVERRIDE')` (design §2.1).
- [x] 1.2 Add `PricingSource` enum `backend/src/main/java/dev/diegobarrioh/tokenmeter/domain/pricing/PricingSource.java` with values `OVERRIDE, REMOTE, FALLBACK` in precedence order (design §3.2; pricing/spec.md "Pricing Source Precedence").
- [x] 1.3 Add `PricingSnapshot` record `domain/pricing/PricingSnapshot.java` wrapping `ModelPricing`, `PricingSource`, `OffsetDateTime fetchedAt`, nullable `externalModelId` with null-checks in compact constructor (design §3.1).
- [x] 1.4 Extend `domain/pricing/AiProvider.java` with `MISTRAL`, `ALIBABA`, `XAI`; add their `configKey()` entries and `fromConfigKey` coverage (pricing/spec.md "Provider Enum Compatibility").
- [x] 1.5 Create `infrastructure/pricing/PricingProperties.java` `@ConfigurationProperties("tokenmeter.pricing")` record with nested `LiteLlm`, `Refresh`, `Admin` records (design §7.1); register via `@EnableConfigurationProperties` on a new `PricingConfig` `@Configuration` class.
- [x] 1.6 Update `backend/src/main/resources/application.yml` with `tokenmeter.pricing.{mapping-location,overrides-location,litellm.{url,timeout},refresh.{enabled,cron,zone,on-startup},admin.enabled}` defaults per design §7.1.
- [x] 1.7 Add profile overrides in `application-local.yml`, `application-docker.yml`, `application-prod.yml`, and `backend/src/test/resources/application.yml` per design §7.2 (refresh disabled in local/test, enabled in docker/prod, admin off in prod).
- [x] 1.8 Expand `backend/src/main/resources/pricing.yaml` to the 17-model Tier 1+2 baseline (pricing/spec.md "Baseline Model Catalogue").
- [x] 1.9 Create `backend/src/main/resources/pricing-mapping.yaml` with the 17 `(provider, model) → litellm-key` entries (design §5.1).
- [x] 1.10 Create empty `backend/src/main/resources/pricing-overrides.yaml.example` (and document that real file is optional / git-ignored if used) (design §5.2).
- [x] 1.11 Verification: run `./gradlew :backend:flywayInfo` (or `./gradlew check` on a clean H2) to confirm V5 applies cleanly. Blocks: 2.x.

## Phase 2: Domain, Persistence & Seed Wiring

Depends: 1.1, 1.2, 1.3, 1.4, 1.5.

- [x] 2.1 Extend `application/pricing/PricingProvider.java` port with `default List<PricingSnapshot> snapshots()` wrapping `all()` (FALLBACK + `Instant.now()`); keep `all()` and `find(...)` unchanged (design §3.1 finalization, §10.4).
- [x] 2.2 Create `infrastructure/persistence/pricing/ModelPricingEntity.java` JPA entity per design §2.2 (no Lombok, explicit constructor + getters, JPA-protected no-args).
- [x] 2.3 Create `infrastructure/persistence/pricing/ModelPricingJpaRepository.java` extending `JpaRepository<ModelPricingEntity, Long>` with `findByProviderAndModel`, `count`, `findAllByOrderByProviderAscModelAsc` (design §2.3).
- [x] 2.4 Create `infrastructure/persistence/pricing/JpaPricingSnapshotStore.java` `@Service` with `count()`, `findAll(): List<PricingSnapshot>`, `replaceAll(List<PricingSnapshot>)` (used by seed), and `@Transactional replaceRemote(List<PricingSnapshot>)` (deletes REMOTE+FALLBACK, inserts REMOTE) per design §6.2.
- [x] 2.5 Add entity↔domain mapper methods in `JpaPricingSnapshotStore` (provider via `AiProvider.configKey()` ↔ `AiProvider.fromConfigKey`, prices to/from `BigDecimal` with scale 6) — no MapStruct (design §2.2 note).
- [x] 2.6 Rename `YamlPricingProvider` bean: remove `@Service`/`@Primary`, register as `@Component("fallbackSeedSource")`; override `snapshots()` to emit `PricingSnapshot(source=FALLBACK, fetchedAt=Instant.now() captured at construction)` (design §1.2, §3.1).
- [x] 2.7 Create `infrastructure/pricing/OverridesPricingLoader.java` `@Component` reading `pricing-overrides.yaml` via `Resource.exists()` guard; exposes `snapshots(): List<PricingSnapshot>` (empty if file missing) (design §5.2).
- [x] 2.8 Create `infrastructure/pricing/CompositePricingProvider.java` `@Component @Primary` implementing `PricingProvider`; merge order = JPA snapshots then OVERRIDE shadowing in a `LinkedHashMap<(provider,model)>`; final sort by `provider.configKey()` then `model` ascending (design §5.3; pricing-api/spec.md "Sorted Model Output").
- [x] 2.9 Create `infrastructure/pricing/FallbackSeedRunner.java` `@Component implements ApplicationRunner`; if `store.count() == 0`, call `fallbackSeedSource.snapshots()` then `store.replaceAll(...)`; log seeded count (design §6.1; pricing/spec.md "Cold-Start Seeding").
- [x] 2.10 Verification: run `./gradlew :backend:test --tests '*Pricing*'` to confirm domain + persistence skeletons compile and existing pricing tests still pass.

## Phase 3: LiteLLM Client + Refresh Service

Depends: Phase 2.

- [x] 3.1 Register a project-scoped `RestClient.Builder` bean (or reuse autoconfigured one) in `PricingConfig` with `tokenmeter.pricing.litellm.timeout` applied (design §4.1).
- [x] 3.2 Create `infrastructure/pricing/litellm/LiteLlmModelEntry.java` package-private record with `@JsonIgnoreProperties(ignoreUnknown=true)` and `input_cost_per_token`, `output_cost_per_token`, `litellm_provider`, `deprecation_date` (design §4.2).
- [x] 3.3 Create `infrastructure/pricing/PricingFetchException.java` `RuntimeException` (design §4.1).
- [x] 3.4 Create `infrastructure/pricing/LiteLlmPricingClient.java` with `fetch(): Map<String, LiteLlmModelEntry>`; map `RestClientException`/empty body to `PricingFetchException` (design §4.1).
- [x] 3.5 Create `infrastructure/pricing/PricingMappingLoader.java` `@Component` parsing `pricing-mapping.yaml` into immutable `Map<MappingKey, String>` (with `record MappingKey(AiProvider provider, String normalizedModel)`); reuse `YamlPricingProvider.normalizeModel` semantics (design §5.1).
- [x] 3.6 Create `infrastructure/pricing/LiteLlmPricingMapper.java` translating raw LiteLLM map + mapping into `List<PricingSnapshot>` (skip `sample_spec`, skip entries with null prices, multiply by `1_000_000` `HALF_UP` scale 6, mark `source=REMOTE`, set `externalModelId`); log warnings for missing mappings/keys (design §4.3, §4.4, §5.1).
- [x] 3.7 Create `application/pricing/refresh/PricingRefreshedEvent.java` record `(OffsetDateTime fetchedAt, int updated, int skipped)` (design §1.2).
- [x] 3.8 Create `application/pricing/refresh/PricingRefreshResult.java` record `(OffsetDateTime fetchedAt, int updated, int skipped, int failed)` returned by refresh service (pricing-api/spec.md "Admin Refresh Endpoint").
- [x] 3.9 Create `application/pricing/refresh/PricingRefreshService.java` `@Service` orchestrating fetch → map → `store.replaceRemote(...)` inside one `@Transactional` boundary; publish `PricingRefreshedEvent` on success; catch `PricingFetchException`/`DataAccessException` and rethrow after metric increment (design §6.2; pricing/spec.md "Transactional Refresh" + "Failed Remote Refresh").
- [x] 3.10 Inject `MeterRegistry`; register counters `tokenmeter_pricing_refresh_success_total`, `tokenmeter_pricing_refresh_failure_total`, and gauge `tokenmeter_pricing_refresh_last_success_timestamp_seconds` (design §9 Open Questions).
- [x] 3.11 Create `infrastructure/pricing/PricingSchedulingConfig.java` `@Configuration @EnableScheduling @ConditionalOnProperty("tokenmeter.pricing.refresh.enabled"=true)` (design §7.3).
- [x] 3.12 Create `infrastructure/pricing/PricingRefreshScheduler.java` `@Component @ConditionalOnProperty(...)` with `@Scheduled(cron=..., zone=...)` calling `service.refresh()`, swallowing `PricingFetchException` so the schedule continues (design §7.3).
- [x] 3.13 Verification: `./gradlew :backend:test --tests '*Refresh*'` plus targeted unit tests added in Phase 6.

## Phase 4: Web Layer

Depends: Phase 3.

- [x] 4.1 Update `infrastructure/web/pricing/PricingModelResponse.java` record to add `source` (String) and `fetchedAt` (Instant/OffsetDateTime); update Jackson defaults to ISO-8601 (pricing-api/spec.md "Pricing Read Response Shape").
- [x] 4.2 Update `infrastructure/web/pricing/PricingResponse.java` to add `lastRefreshedAt` (nullable Instant) and `primarySource` (String); compute `lastRefreshedAt = max(fetchedAt where source=REMOTE)` else `null`, `primarySource = "litellm" | "fallback" | "mixed"` (design §6.3).
- [x] 4.3 Update `PricingController` (or its mapper) to consume `PricingProvider.snapshots()` and sort by `provider.configKey()` then `model` (pricing-api/spec.md "Sorted Model Output").
- [x] 4.4 Create `infrastructure/web/pricing/PricingAdminController.java` `@RestController` `@ConditionalOnProperty("tokenmeter.pricing.admin.enabled", havingValue="true", matchIfMissing=true)` with `POST /api/admin/pricing/refresh` returning `202 Accepted` and `PricingRefreshResult` body (pricing-api/spec.md "Admin Refresh Endpoint").
- [x] 4.5 Add a 503 fallback: when `tokenmeter.pricing.admin.enabled=false`, controller bean is absent — add a no-op `@RestController` shim or `RequestMappingHandlerMapping` advice returning 503 for `POST /api/admin/pricing/refresh` (pricing-api/spec.md "Admin Refresh Disabled State"). Simpler alternative: keep the controller always loaded, gate behaviour on `properties.admin().enabled()` and return 503 otherwise — implement this variant.
- [x] 4.6 Create `infrastructure/web/pricing/PricingExceptionHandler.java` `@RestControllerAdvice(assignableTypes = PricingAdminController.class)` mapping `PricingFetchException` → 503 with `{error, message}` body (design §6.4).
- [x] 4.7 Update OpenAPI/Swagger annotations (or `docs/API.md` references) on both controllers to reflect new fields and the admin endpoint.
- [x] 4.8 Verification: `./gradlew :backend:test --tests '*PricingController*' --tests '*PricingAdminController*'`.

## Phase 5: Frontend

Depends: 4.1, 4.2, 4.4.

- [x] 5.1 Update `frontend/src/types/api.ts`: extend `PricingResponse` with `lastRefreshedAt`, `primarySource`; extend `PricingModelResponse` with `source` (`'REMOTE'|'FALLBACK'|'OVERRIDE'`), `fetchedAt`, optional `externalModelId` (design §8.1).
- [x] 5.2 Update `frontend/src/pages/ModelsPage.tsx` state to `PricingResponse | null`; adjust fetch effect accordingly.
- [x] 5.3 Add a freshness banner above the table in `ModelsPage.tsx`: when `lastRefreshedAt` is non-null show "Updated {relative} — source: LiteLLM upstream", otherwise "Showing fallback prices…" (design §8.2).
- [x] 5.4 Add a "Source" column to the pricing table with pill styling (REMOTE green, FALLBACK amber, OVERRIDE purple), reusing the `providerBadgeCls`-style helper.
- [x] 5.5 Implement a small relative-time helper using `Intl.RelativeTimeFormat` (no new deps); respect user locale via `navigator.language` (design §8.2).
- [x] 5.6 Add provider badge classes for `mistral`, `alibaba`, `xai` in `ModelsPage.tsx` for visual consistency (design §3.3 frontend note).
- [x] 5.7 Verification: `cd frontend && npm run lint && npm run build`.

## Phase 6: Tests

May start once Phase 2 is in place; each subsection unblocks as its production code lands.

### 6.A Unit tests

- [x] 6.1 `domain/pricing/PricingSnapshotTest` — null arguments throw; accessor delegation to `ModelPricing` (Phase 1.3).
- [x] 6.2 `domain/pricing/AiProviderTest` — `fromConfigKey("mistral"|"alibaba"|"xai")` round-trips; legacy keys still resolve (pricing/spec.md "Provider Enum Compatibility").
- [x] 6.3 `infrastructure/pricing/PricingMappingLoaderTest` — loads 17 entries; normalizes lower-case; missing file path raises explicit error.
- [x] 6.4 `infrastructure/pricing/OverridesPricingLoaderTest` — missing file → empty list; happy path → snapshots tagged `OVERRIDE`.
- [x] 6.5 `infrastructure/pricing/LiteLlmPricingMapperTest` — fixture `backend/src/test/resources/fixtures/litellm-sample.json` (≤20 entries) covers: per-token → per-million conversion, `sample_spec` skipped, null-cost row skipped, missing mapping logged WARN and skipped (pricing/spec.md "Successful Remote Refresh" + scenarios).
- [x] 6.6 `application/pricing/refresh/PricingRefreshServiceTest` (Mockito) — success: `store.replaceRemote` called once, event published, counter incremented; client throws `PricingFetchException` → store untouched, failure counter +1 (pricing/spec.md "Failed Remote Refresh" all 3 scenarios).
- [x] 6.7 `infrastructure/pricing/CompositePricingProviderTest` — given mocked store + override loader, OVERRIDE shadows REMOTE, REMOTE shadows FALLBACK, ordering ascending (pricing/spec.md "Pricing Source Precedence"; pricing-api/spec.md "Sorted Model Output").

### 6.B Slice / Persistence tests

- [x] 6.8 `infrastructure/persistence/pricing/JpaPricingSnapshotStoreTest` `@DataJpaTest` — `replaceRemote` deletes REMOTE+FALLBACK and inserts REMOTE atomically; throwing during insert rolls back (pricing/spec.md "Transactional Refresh" — "Partial write is rolled back").
- [x] 6.9 `infrastructure/web/pricing/PricingControllerTest` `@WebMvcTest` — response includes `lastRefreshedAt`, `primarySource`, per-row `source`+`fetchedAt`; ordering ascending; cold-start path (all FALLBACK) returns 200 (pricing-api/spec.md "Pricing Read Resilience").
- [x] 6.10 `infrastructure/web/pricing/PricingAdminControllerTest` `@WebMvcTest` — POST returns 202 with `updated/skipped/failed`; `PricingFetchException` → 503; `admin.enabled=false` profile slice → 503 (pricing-api/spec.md "Admin Refresh Endpoint" + "Admin Refresh Disabled State").

### 6.C Integration tests

- [x] 6.11 `FallbackSeedRunnerIntegrationTest` `@SpringBootTest` — empty H2 boot seeds exactly 17 FALLBACK rows; second boot does not duplicate (pricing/spec.md both Cold-Start scenarios).
- [x] 6.12 `PricingRefreshIntegrationTest` `@SpringBootTest` with a stubbed `LiteLlmPricingClient` (TestConfiguration) returning the fixture — end-to-end refresh upgrades rows to REMOTE; failure path leaves rows intact (pricing/spec.md "Successful Remote Refresh" + "Failed Remote Refresh").
- [x] 6.13 `CompositePricingProviderIntegrationTest` `@SpringBootTest` — overrides file present → OVERRIDE rows shadow stored REMOTE (pricing/spec.md "Override-shadows-remote-and-fallback").

### 6.D Regression + cost

- [x] 6.14 Re-run `RepositoryCostEstimationServiceTest` unchanged; assert it still passes (cost-estimation/spec.md "Pricing-Input Determinism", "Cost Computation Formula Preserved", "Estimate Cardinality").
- [x] 6.15 Add `RepositoryCostEstimationServiceSnapshotTest` parametrised with the same `ModelPricing` set tagged once as REMOTE and once as FALLBACK; assert element-wise equal estimates (cost-estimation/spec.md "Same prices yield same estimates across sources").
- [x] 6.16 Add `RepositoryCostEstimationServiceRefreshTest` simulating `PricingRefreshedEvent` between two `estimate` calls; assert second call uses new prices (cost-estimation/spec.md "Refresh between estimations is observed"; "Mid-analysis consistency" — assert single-snapshot capture per call).

### 6.E Frontend

- [x] 6.17 If a vitest/RTL harness exists, add `ModelsPage.test.tsx` covering banner copy (refreshed vs fallback) and source pill rendering; otherwise document the gap in `frontend/README.md` and mark this task complete with a TODO link. (No harness installed; gap documented in `frontend/README.md` with TODO marker.)

### 6.F Verification

- [x] 6.18 Run `./gradlew check` end-to-end (includes `spotlessCheck`, `checkstyleMain`, all tests).
- [x] 6.19 Run `cd frontend && npm run lint && npm run build`.

## Phase 7: Docs & Ops

Depends: Phases 1–6.

- [x] 7.1 Update `docs/ARCHITECTURE.md` with the new pricing flow: composite provider, refresh sequence diagram (lift from design §6.1–§6.3), table schema reference (design §2.1).
- [x] 7.2 Update `docs/API.md` with the extended `GET /api/pricing` response shape and the new `POST /api/admin/pricing/refresh` endpoint (states: 202 success, 503 disabled/upstream-failure).
- [x] 7.3 Update `README.md` env vars block with `tokenmeter.pricing.refresh.enabled`, `tokenmeter.pricing.refresh.cron`, `tokenmeter.pricing.litellm.url`, `tokenmeter.pricing.admin.enabled` and the new `pricing-mapping.yaml` / `pricing-overrides.yaml` resources.
- [x] 7.4 Update `CLAUDE.md` no-go zones: forbid editing the new V5 migration once applied; remind that overrides YAML must never be committed with real negotiated rates.
- [x] 7.5 Add a runbook section (e.g. `docs/RUNBOOK.md` or `docs/OPERATIONS.md`) describing how to interpret `tokenmeter_pricing_refresh_failure_total`, how to trigger a manual refresh, and how to roll back per design §10.
- [x] 7.6 Final verification: `./gradlew check && (cd frontend && npm run build)` passed. Docker-compose smoke test (`docker compose up --build -d`) deferred to the orchestrator because it requires `TOKENMETER_DB_USER`/`TOKENMETER_DB_PASSWORD` env vars (per `CLAUDE.md` policy — agents do not invent secrets). Recipe documented in `docs/RUNBOOK.md` § "Smoke test recipe".
