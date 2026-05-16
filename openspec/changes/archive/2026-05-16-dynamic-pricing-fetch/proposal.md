# Proposal: Dynamic Pricing Fetch

## Intent

Replace the static `pricing.yaml` (4 models, hand-maintained) with a dynamic pricing pipeline that fetches per-model token costs from the LiteLLM aggregator, persists snapshots, refreshes on a schedule, and exposes freshness metadata to the UI. Goal: keep TokenMeter cost estimates aligned with real provider rates without manual edits.

## Scope

### In Scope
- F1 — Snapshot persistence: Flyway `V5__model_pricing_snapshot.sql`; `CompositePricingProvider` (overrides → DB → YAML seed); `ApplicationRunner` seeds DB from YAML on cold-start.
- F2 — LiteLLM client + mapping: `LiteLlmPricingClient` (Spring `RestClient`); `pricing-mapping.yaml` (internal `provider:model` → LiteLLM key); `PricingRefreshService`; admin endpoint `POST /api/admin/pricing/refresh`.
- F3 — Scheduled refresh: `@EnableScheduling`; cron `0 0 3 * * MON` (configurable); Prometheus metrics for refresh success/failure.
- F4 — Frontend: `ModelsPage.tsx` shows last-refresh timestamp + per-row source badge (`LIVE`/`FALLBACK`/`OVERRIDE`).
- F5 — Override layer: optional `pricing-overrides.yaml` for negotiated/corporate prices, highest precedence.
- Tier 1+2 catalogue (17 models). `AiProvider` enum extension: `MISTRAL`, `ALIBABA`, `XAI`.

### Out of Scope
- Tier 3 legacy models.
- Tiered pricing (>200k context surcharge).
- Cache-read pricing (`cache_read_input_token_cost`).
- Hosters (Together/Groq/Fireworks).
- Multi-currency (USD only).
- Auth on admin endpoint (feature-flag for now).

## Approach

Hybrid layering: overrides YAML → `model_pricing` table (REMOTE rows from LiteLLM) → seed YAML (FALLBACK rows). Single Spring `@Primary` `CompositePricingProvider` reads on each call (N≈17, no cache). `PricingRefreshService` orchestrates fetch → map → transactional upsert; emits `PricingRefreshedEvent`. Boot never blocks on remote; failures keep prior rows.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `domain/pricing/AiProvider.java` | Modified | +3 enum values |
| `domain/pricing/ModelPricing.java` | Modified | +`source`, +`fetchedAt` |
| `infrastructure/pricing/` | New | client, mapper, composite, refresh-service, override loader |
| `infrastructure/persistence/pricing/` | New | JPA entity + repo + store |
| `db/migration/V5__model_pricing_snapshot.sql` | New | table `model_pricing` (Flyway bump V4→V5) |
| `pricing.yaml` | Modified | Expanded to 17-model baseline |
| `pricing-mapping.yaml`, `pricing-overrides.yaml` | New | mapping + override files |
| `application.yml` | Modified | `tokenmeter.pricing.*` config block |
| `TokenmeterApplication.java` | Modified | `@EnableScheduling` |
| `infrastructure/web/pricing/*` | Modified | Response shape + admin endpoint |
| `frontend/src/types/api.ts`, `pages/ModelsPage.tsx` | Modified | Freshness UI |
| `docs/ARCHITECTURE.md` | Modified | Document new flow |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| LiteLLM JSON shape drift | Med | Contract test against checked-in fixture; failure counter; transactional refresh (no partial writes) |
| Internal↔external name mismatch | High | Explicit `pricing-mapping.yaml`; missing key logged warn, row untouched |
| LiteLLM lags official price drops | Med | Override YAML escape hatch |
| GitHub raw rate-limit (60/h) | Low | Weekly cron + on-demand refresh; switch to GitHub API + PAT only if cadence increases |
| JPA `validate` rejects new entity | Low | V5 ships in same commit as entity; H2 dialect verified in tests |
| `@EnableScheduling` fires in tests | Low | `tokenmeter.pricing.refresh.enabled=false` in `application-test.yml` |

## Rollback Plan

1. Set `tokenmeter.pricing.refresh.enabled=false` to halt scheduled fetches.
2. Revert `CompositePricingProvider` registration; re-mark `YamlPricingProvider` `@Primary` (one-line change).
3. Flyway V5 leaves an empty/unused table — no schema rollback needed; can drop later via `V6__drop_model_pricing.sql` if abandoned.
4. Frontend reverts by removing freshness fields from `types/api.ts` consumers.
5. Tag release before merge (`vX.Y.Z-pre-dynamic-pricing`) for quick git revert path.

## Dependencies

- External: `https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json` (MIT-licensed, community-maintained).
- Internal: none beyond what's already in `build.gradle.kts` (Spring `RestClient`, Jackson, Flyway, JPA).

## Success Criteria

- [ ] `GET /api/pricing` returns 17 models with `source` + `fetchedAt` populated.
- [ ] Cold-start with empty DB seeds 17 rows from YAML (`source=FALLBACK`).
- [ ] `POST /api/admin/pricing/refresh` (or scheduled trigger) replaces fallback rows with `source=REMOTE` and current `fetchedAt`.
- [ ] Refresh failure leaves prior rows intact; metric counter increments.
- [ ] Override row shadows REMOTE for the same `(provider, model)` key.
- [ ] `ModelsPage` displays last-refresh timestamp and per-row source badge.
- [ ] `./gradlew check` + `npm run build` + `npm run lint` all green.
- [ ] No regression in `RepositoryCostEstimationService` output for identical input prices.
