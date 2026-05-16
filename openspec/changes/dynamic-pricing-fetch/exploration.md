# Exploration: dynamic-pricing-fetch

**Scope**: F1–F5 — fetch model pricing from an external aggregator, persist snapshots, refresh on schedule, allow overrides, and surface freshness in UI. Models = Tier 1 + Tier 2 (~17 models), no third-party hosters.

---

## Current State

Pricing today is **fully static**: `backend/src/main/resources/pricing.yaml` is read at startup by `YamlPricingProvider` (`@Service`), parsed into an immutable `List<ModelPricing>`, and exposed via:

- `RepositoryCostEstimationService.estimate(long baseTokens)` — iterates `pricingProvider.all()` for every analysis, producing one `ModelCostEstimate` per (model × `CostEstimationMode`) combination. **Hot path** — runs on every `POST /api/analyze`.
- `GET /api/pricing` (`PricingController`) — public read endpoint returning the same list.
- Frontend `ModelsPage.tsx` consumes `/api/pricing` → renders pricing table. No freshness metadata today.

Domain shape (`ModelPricing`): `provider` (`AiProvider` enum: `OPENAI`, `ANTHROPIC`, `GOOGLE`, `DEEPSEEK`), `model` (string), `inputTokenPricePerMillion` (`BigDecimal`), `outputTokenPricePerMillion` (`BigDecimal`).

Stack-relevant facts:
- Java 21 + Spring Boot 3.5 → native `RestClient` available, no need for WebClient.
- PostgreSQL 18 + Flyway → next migration is `V5__*.sql` (V4 = `leaderboard_indexes`).
- JPA `ddl-auto: validate` → schema must come from Flyway.
- `pricing.yaml` has 4 models; Tier 1+2 list requires ~13 new entries.
- No Spring scheduling enabled today (no `@EnableScheduling` anywhere).
- No outbound HTTP today (`GitCliRepositoryCloner` shells out to `git`).
- Throttle / rate-limit config in `tokenmeter.analyze-throttle` shows the project already has pattern for nested config under `tokenmeter.*`.
- `AiProvider` enum needs extension: `MISTRAL`, `ALIBABA` (Qwen), `XAI` (Grok), `META` (Llama, if kept). Per scope "no hosters" → drop Llama. Per Tier 2 list → add Mistral, Alibaba, xAI.

---

## Affected Areas

| Path | Reason |
|------|--------|
| `domain/pricing/AiProvider.java` | Add `MISTRAL`, `ALIBABA`, `XAI` enum values |
| `domain/pricing/ModelPricing.java` | Add `source` + `fetchedAt` fields (or wrap in a new record) |
| `application/pricing/PricingProvider.java` | Contract may expose `lastRefreshedAt()` or freshness per model |
| `infrastructure/pricing/YamlPricingProvider.java` | Rename / repurpose as **fallback** seed source |
| `infrastructure/pricing/` (new) | `LiteLlmPricingClient`, `LiteLlmPricingMapper`, `CompositePricingProvider`, `PricingRefreshService` |
| `infrastructure/persistence/pricing/` (new) | `ModelPricingEntity`, `ModelPricingJpaRepository`, `JpaPricingSnapshotStore` |
| `backend/src/main/resources/db/migration/V5__model_pricing_snapshot.sql` | New table `model_pricing` |
| `backend/src/main/resources/pricing.yaml` | Expand to Tier 1+2 baseline (acts as fallback if remote fetch fails AND DB empty) |
| `backend/src/main/resources/pricing-mapping.yaml` (new) | Internal `provider:model` → external LiteLLM key mapping |
| `backend/src/main/resources/pricing-overrides.yaml` (new, optional) | Manual overrides for negotiated/corporate prices |
| `application.yml` | `tokenmeter.pricing.*` config block (source URL, refresh cron, timeout, enabled flags) |
| `application-test.yml` | Disable remote refresh in tests |
| `TokenmeterApplication.java` | Add `@EnableScheduling` |
| `infrastructure/web/pricing/PricingController.java` | Add `fetchedAt`, `source` to response. New `POST /api/admin/pricing/refresh` |
| `infrastructure/web/pricing/PricingResponse.java`, `PricingModelResponse.java` | Add fields |
| `application/cost/RepositoryCostEstimationService.java` | Verify behavior with mutable provider (still calls `all()` per analysis → no caching change needed) |
| `frontend/src/types/api.ts` | Add `fetchedAt`, `source`, `lastRefreshedAt` |
| `frontend/src/pages/ModelsPage.tsx` | Render "Updated X ago" + source badge (live/fallback/override) |
| `build.gradle.kts` | No new deps — Spring `RestClient` + Jackson already present |
| `docs/ARCHITECTURE.md` | Document new pricing flow + Flyway V5 |

---

## Approaches

### 1. **LiteLLM JSON as single source of truth** (recommended)
Fetch `https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json` on a schedule, map via `pricing-mapping.yaml`, persist snapshot in `model_pricing` table.

- **Pros**:
  - Single HTTP call, stable JSON, MIT-licensed, community-maintained (covers ~400 models).
  - Per-token cost field is trivial: `× 1_000_000` → matches our `pricePerMillion`.
  - No scraping, no ToS risk, no 4 brittle parsers.
  - LiteLLM is the de-facto pricing aggregator used by many open-source AI tools.
- **Cons**:
  - One upstream dependency; if their JSON format changes we break.
  - Provider name in their JSON (`litellm_provider`) sometimes doesn't match our enum (e.g., `bedrock_converse` vs `anthropic`). Requires explicit mapping file.
  - Lag of hours/days vs official provider page on price drops.
  - GitHub raw rate limit (60 req/h unauth) — fine for weekly cron, would matter if hourly.
- **Effort**: Medium.

### 2. **Hybrid: LiteLLM + manual YAML overrides**
Same as #1 but with an explicit `pricing-overrides.yaml` layered on top (highest precedence). Useful for: corporate-negotiated rates, models LiteLLM hasn't catalogued yet, or temporary fixes.

- **Pros**: Same as #1 plus escape hatch.
- **Cons**: One more layer to reason about.
- **Effort**: Medium (delta over #1 is small).

### 3. **OpenRouter `/api/v1/models`**
No-auth REST endpoint returning prices for all models hosted on OpenRouter.

- **Pros**: Official-ish, JSON, stable schema, no GitHub dep.
- **Cons**: Prices are OpenRouter's markups, not direct provider pricing. Users analyzing repos likely want direct provider cost. Provider-direct models (DeepSeek API, Anthropic API direct) may not match.
- **Effort**: Medium.

### 4. **Direct provider HTML scraping**
Jsoup parsers per provider against `platform.claude.com/docs/...`, `openai.com/api/pricing`, `ai.google.dev/.../pricing`, `api-docs.deepseek.com/...`.

- **Pros**: Fresh, official source.
- **Cons**: 4 brittle parsers, breaks on every layout change, ToS gray area (anti-bot, terms-of-use), high maintenance burden. No structured data for several providers; tier pricing usually in tables that look identical to humans but render differently across releases. **Rejected**.
- **Effort**: High.

### 5. **No external fetch — manual YAML updates only**
Keep `pricing.yaml`, document a manual update workflow.

- **Pros**: Zero infra cost, zero risk.
- **Cons**: Doesn't meet user's stated goal of "dynamic prices from current provider rates". **Rejected as primary solution** but it's effectively the F5 fallback layer when remote fetch fails.
- **Effort**: Zero (current state).

---

## Recommendation

**Approach #2: LiteLLM + overrides layer (hybrid).**

Architecture:

```
PricingProvider (interface, unchanged)
        ▲
        │
CompositePricingProvider
  │ resolution order:
  │   1. pricing-overrides.yaml  (source: OVERRIDE)
  │   2. model_pricing table     (source: REMOTE — populated by refresh)
  │   3. pricing.yaml seed       (source: FALLBACK)
```

Components:

| Component | Layer | Responsibility |
|-----------|-------|----------------|
| `LiteLlmPricingClient` | infrastructure | HTTP GET + JSON parse; returns raw `LiteLlmModelEntry[]` |
| `PricingMappingLoader` | infrastructure | Reads `pricing-mapping.yaml` → `Map<(AiProvider, internalModel), litellmKey>` |
| `LiteLlmPricingMapper` | infrastructure | Filters by mapping, converts `× 1_000_000`, builds `ModelPricing` w/ `source=REMOTE, fetchedAt=now` |
| `JpaPricingSnapshotStore` | infrastructure | Upsert into `model_pricing` table |
| `PricingRefreshService` | application | Orchestrates fetch → map → persist; emits `PricingRefreshedEvent` |
| `OverridesPricingLoader` | infrastructure | Reads `pricing-overrides.yaml` if present |
| `CompositePricingProvider` | infrastructure | Replaces `YamlPricingProvider` as the `@Primary` `PricingProvider` bean. Reads `model_pricing` + overrides on each call (small N, no caching needed). |
| `FallbackSeedLoader` | infrastructure (start-up `ApplicationRunner`) | If `model_pricing` empty → seed from `pricing.yaml` with `source=FALLBACK` |
| `PricingAdminController` (feature-flagged) | infrastructure/web | `POST /api/admin/pricing/refresh` for manual trigger |

Schedule: `@Scheduled(cron = "0 0 3 * * MON")` (Mondays 03:00 UTC). Configurable via `tokenmeter.pricing.refresh.cron`. Enabled in `prod` / `docker`, disabled in `test`.

Boot order:
1. Flyway migrates `V5`.
2. `FallbackSeedLoader` runs (ApplicationRunner) — if table empty, seed from YAML.
3. Optional eager refresh: if `tokenmeter.pricing.refresh.on-startup=true`, async fetch fires post-boot.
4. Scheduled refresh fires on cron.

Failure handling:
- Remote fetch timeout/4xx/5xx → log warn, keep existing DB rows, do not corrupt them. Metric `tokenmeter_pricing_refresh_failures_total`.
- Mapping miss for a configured internal model → log warn, leave that model's prior row untouched.
- JSON parse error → fail the whole refresh, no partial writes (transactional).

Cost estimation impact:
- `RepositoryCostEstimationService` still iterates `pricingProvider.all()` per analysis. With ~17 models and three modes, that's 51 estimates per analysis, identical to today's structure — no perf concern.
- If a refresh fires mid-analysis, the iteration sees a consistent snapshot because we replace rows in a single transaction.

API response shape (new):
```json
{
  "lastRefreshedAt": "2026-05-13T03:00:00Z",
  "primarySource": "litellm",
  "models": [
    {
      "provider": "anthropic",
      "model": "claude-opus-4-7",
      "inputTokenPricePerMillion": 15.00,
      "outputTokenPricePerMillion": 75.00,
      "source": "REMOTE",
      "fetchedAt": "2026-05-13T03:00:00Z"
    }
  ]
}
```

Frontend `ModelsPage.tsx` additions:
- Top-of-table banner: "Last updated 2 days ago — source: LiteLLM upstream".
- Per-row pill: `LIVE` / `FALLBACK` / `OVERRIDE`.
- Empty state if everything is fallback: warn user prices may be stale.

---

## Tier 1+2 Model Catalogue (no hosters)

Initial `pricing.yaml` baseline + mapping seed. Mapping keys are upstream LiteLLM identifiers — exact strings to be verified during F1/F2 against the live JSON; mapping file is the source of truth for adjustments.

| Tier | Provider (`AiProvider`) | Internal `model` | LiteLLM key (initial) |
|------|-------------------------|------------------|------------------------|
| 1 | `ANTHROPIC` | `claude-opus-4-7` | `claude-opus-4-7` |
| 1 | `ANTHROPIC` | `claude-sonnet-4-6` | `claude-sonnet-4-6` |
| 1 | `ANTHROPIC` | `claude-haiku-4-5` | `claude-haiku-4-5-20251001` |
| 1 | `OPENAI` | `gpt-5` | `gpt-5` |
| 1 | `OPENAI` | `gpt-4o` | `gpt-4o` |
| 1 | `OPENAI` | `gpt-4o-mini` | `gpt-4o-mini` |
| 1 | `OPENAI` | `o3` | `o3` |
| 1 | `OPENAI` | `o4-mini` | `o4-mini` |
| 1 | `GOOGLE` | `gemini-2.5-pro` | `gemini-2.5-pro` |
| 1 | `GOOGLE` | `gemini-2.5-flash` | `gemini-2.5-flash` |
| 1 | `DEEPSEEK` | `deepseek-chat` | `deepseek-chat` |
| 1 | `DEEPSEEK` | `deepseek-reasoner` | `deepseek-reasoner` |
| 2 | `ALIBABA` | `qwen3-coder` | `qwen/qwen3-coder` (verify) |
| 2 | `ALIBABA` | `qwen3-max` | `qwen/qwen3-max` (verify) |
| 2 | `XAI` | `grok-4` | `grok-4` (verify) |
| 2 | `MISTRAL` | `mistral-large-2` | `mistral-large-latest` |
| 2 | `MISTRAL` | `codestral` | `codestral-latest` |

`AiProvider` enum additions: `MISTRAL`, `ALIBABA`, `XAI`. Provider order in displays sorted by `configKey()` — stable across the codebase.

Decisions deferred to spec/design:
- Reasoning model `cache_read_input_token_cost`: ignore in v1 (already implicit in `CostEstimationMode` multipliers).
- Tier pricing (>200k context surcharge): ignore in v1, use base price.
- Legacy Tier 3 models: not in scope; archive note for future.

---

## Risks

- **LiteLLM JSON shape drift** — schema changes silently break refresh. Mitigation: contract test against a checked-in fixture + Prometheus counter for parse failures. Alert if 2 consecutive refreshes fail.
- **Model name mismatch** between LiteLLM key and our internal name — mapping file is the explicit contract. Missing mapping → no update for that model (logged, not fatal).
- **GitHub raw rate-limit** (60/hour unauthenticated) — weekly cron is fine. If we later increase frequency we'll need a GitHub PAT or move to GitHub API.
- **Network failures during build container start** — refresh is async post-boot, app starts cleanly without it. Seed YAML guarantees we never serve an empty `/api/pricing`.
- **JPA `ddl-auto: validate`** + new table — must add Flyway V5 **before** the new entity is mapped, otherwise `bootRun` fails. Tests use H2 which also runs Flyway; verify H2 dialect compatibility for the SQL we write (use plain types, avoid Postgres-only features).
- **`@EnableScheduling` in tests** — must be conditional. Add `@ConditionalOnProperty("tokenmeter.pricing.refresh.enabled", havingValue="true", matchIfMissing=true)` on the scheduler bean; set `false` in `application-test.yml`.
- **Outdated upstream prices** — LiteLLM can lag official price drops by days. Acceptable for our use case (cost *estimate*); override layer lets us patch urgent corrections.
- **Tier 2 enum churn** — adding `XAI`/`ALIBABA`/`MISTRAL` to `AiProvider` requires updating: provider badges in frontend (`providerBadgeCls`), tests that exhaustively switch on the enum (search for `switch.*AiProvider`), and any persisted strings (none today — provider is stored as the enum `configKey()` string in `cost_estimates` via JPA, so new values just round-trip).
- **DB migration on running prod** — V5 creates an empty table; safe and idempotent. No data backfill in the migration itself; backfill happens at runtime via the seed loader.

---

## Ready for Proposal

**Yes.** Scope is clear, recommended approach (#2) is concrete, and the F1–F5 phasing already exists in the orchestrator's plan. Suggested follow-up: `sdd-propose` with this exploration as input. Spec will need to enumerate scenarios for: (a) cold-start with empty DB → fallback seed, (b) successful refresh → REMOTE rows replace FALLBACK, (c) refresh failure → existing rows untouched, (d) override file present → OVERRIDE rows shadow REMOTE, (e) `/api/pricing` response shape with new fields, (f) `RepositoryCostEstimationService` continues producing identical estimates given identical prices.
