# Pricing Specification

## Purpose

Defines the pricing provider contract, refresh lifecycle, source precedence (override > remote snapshot > YAML seed), and the baseline model catalogue. Pricing data is consumed by cost estimation and exposed by the pricing API; this spec governs how pricing data enters and lives in the system.

## Requirements

### Requirement: Pricing Source Precedence

The system MUST resolve each `(provider, model)` pricing entry in this strict order: (1) override layer (`pricing-overrides.yaml`), (2) remote snapshot rows in `model_pricing` table, (3) YAML seed (`pricing.yaml`) materialised in the table with `source=FALLBACK`. The selected row's `source` field MUST reflect the winning layer (`OVERRIDE`, `REMOTE`, or `FALLBACK`).

#### Scenario: Override shadows remote and fallback

- GIVEN `model_pricing` has a row for `(ANTHROPIC, claude-opus-4-7)` with `source=REMOTE`
- AND `pricing-overrides.yaml` contains an entry for the same `(provider, model)`
- WHEN the pricing provider returns the active snapshot
- THEN the entry for `(ANTHROPIC, claude-opus-4-7)` MUST carry the override's prices and `source=OVERRIDE`

#### Scenario: Remote shadows fallback

- GIVEN no override is configured for `(OPENAI, gpt-5)`
- AND `model_pricing` contains a `REMOTE` row for `(OPENAI, gpt-5)`
- WHEN the pricing provider returns the active snapshot
- THEN the `(OPENAI, gpt-5)` entry MUST carry the REMOTE prices and `source=REMOTE`

### Requirement: Cold-Start Seeding

The system MUST seed the `model_pricing` table from `pricing.yaml` on application start when the table is empty, marking every seeded row with `source=FALLBACK` and `fetchedAt` set to the seeding instant.

#### Scenario: Empty table is seeded on boot

- GIVEN the application starts and `model_pricing` is empty
- WHEN the seed loader runs after Flyway migrations complete
- THEN every model in `pricing.yaml` MUST be inserted into `model_pricing` with `source=FALLBACK`
- AND each inserted row's `fetchedAt` MUST equal the boot-time seeding instant

#### Scenario: Non-empty table is not reseeded

- GIVEN the application starts and `model_pricing` already contains at least one row
- WHEN the seed loader runs
- THEN it MUST NOT modify, insert, or delete any existing row

### Requirement: Successful Remote Refresh

A successful LiteLLM refresh MUST replace rows for every mapped model with `source=REMOTE` and the current `fetchedAt`, and MUST leave unmapped or upstream-missing models untouched.

#### Scenario: Mapped models are updated

- GIVEN `pricing-mapping.yaml` maps 17 internal `(provider, model)` pairs to LiteLLM keys
- AND the LiteLLM payload contains valid prices for all 17 mapped keys
- WHEN `PricingRefreshService` completes a refresh
- THEN every mapped row in `model_pricing` MUST have `source=REMOTE`
- AND every mapped row's `fetchedAt` MUST equal the refresh instant
- AND each row's input/output prices MUST equal the upstream value multiplied by 1_000_000

#### Scenario: Upstream missing key keeps prior row

- GIVEN `(MISTRAL, codestral)` is mapped but the LiteLLM payload omits its key
- WHEN the refresh runs
- THEN the existing `model_pricing` row for `(MISTRAL, codestral)` MUST remain unchanged
- AND a warning MUST be logged for the missing key

#### Scenario: Unmapped internal model is never updated

- GIVEN `(GOOGLE, gemini-experimental)` exists in `model_pricing` but has no entry in `pricing-mapping.yaml`
- WHEN the refresh runs
- THEN the row for `(GOOGLE, gemini-experimental)` MUST NOT be updated by the refresh
- AND a warning MUST be logged for the unmapped model

### Requirement: Failed Remote Refresh

When a refresh fails (connection timeout, non-2xx HTTP status, malformed JSON, or any parser error), the system MUST leave all rows in `model_pricing` untouched and MUST increment a refresh-failure counter.

#### Scenario: Network timeout leaves data intact

- GIVEN the refresh is triggered and the upstream request times out
- WHEN `PricingRefreshService` handles the failure
- THEN no row in `model_pricing` MUST be modified
- AND the refresh-failure metric counter MUST be incremented by 1

#### Scenario: Malformed JSON aborts refresh

- GIVEN the upstream returns a 200 response with invalid JSON
- WHEN `PricingRefreshService` attempts to parse the payload
- THEN no row in `model_pricing` MUST be modified
- AND the refresh-failure metric counter MUST be incremented by 1

#### Scenario: 5xx status aborts refresh

- GIVEN the upstream returns HTTP 503
- WHEN `PricingRefreshService` handles the response
- THEN no row in `model_pricing` MUST be modified
- AND the refresh-failure metric counter MUST be incremented by 1

### Requirement: Transactional Refresh

The refresh MUST be transactional: either every mapped model row updates successfully or none of them do.

#### Scenario: Partial write is rolled back

- GIVEN a refresh is in progress and 16 of 17 mapped rows have been staged for update
- WHEN persistence of the 17th row throws a `DataAccessException`
- THEN every staged change in that refresh MUST be rolled back
- AND `model_pricing` MUST reflect the state from before the refresh started

### Requirement: Baseline Model Catalogue

The initial baseline catalogue (used by `pricing.yaml` and `pricing-mapping.yaml`) MUST contain the following 17 Tier 1+2 `(provider, model)` pairs: `(ANTHROPIC, claude-opus-4-7)`, `(ANTHROPIC, claude-sonnet-4-6)`, `(ANTHROPIC, claude-haiku-4-5)`, `(OPENAI, gpt-5)`, `(OPENAI, gpt-4o)`, `(OPENAI, gpt-4o-mini)`, `(OPENAI, o3)`, `(OPENAI, o4-mini)`, `(GOOGLE, gemini-2.5-pro)`, `(GOOGLE, gemini-2.5-flash)`, `(DEEPSEEK, deepseek-chat)`, `(DEEPSEEK, deepseek-reasoner)`, `(ALIBABA, qwen3-coder)`, `(ALIBABA, qwen3-max)`, `(XAI, grok-4)`, `(MISTRAL, mistral-large-2)`, `(MISTRAL, codestral)`.

#### Scenario: Baseline catalogue is exhaustive

- GIVEN the application is freshly seeded from `pricing.yaml`
- WHEN the pricing provider returns its snapshot
- THEN the snapshot MUST contain exactly the 17 baseline `(provider, model)` pairs listed above

### Requirement: Provider Enum Compatibility

The `AiProvider` enum MUST be extended to include `MISTRAL`, `ALIBABA`, and `XAI`, and existing `cost_estimates` rows persisted before this change MUST remain readable without migration.

#### Scenario: Existing cost_estimates rows still load

- GIVEN `cost_estimates` contains rows persisted before the enum extension referencing `OPENAI`, `ANTHROPIC`, `GOOGLE`, and `DEEPSEEK`
- WHEN those rows are read through the JPA repository after the enum is extended
- THEN every row MUST deserialise without error
- AND the provider field MUST map to the original enum value

#### Scenario: New providers round-trip through persistence

- GIVEN a cost estimate is computed for `(MISTRAL, codestral)`
- WHEN it is persisted and reloaded
- THEN the reloaded row's provider MUST equal `MISTRAL`
