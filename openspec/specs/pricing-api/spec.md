# Pricing API — Main Spec

Source: archived change `dynamic-pricing-fetch` (2026-05-16). Updated by future changes via additional deltas.

---

# Pricing API Specification

## Purpose

Defines the HTTP contract for pricing-related endpoints: the public read endpoint `GET /api/pricing` (extended with freshness metadata) and the new admin endpoint `POST /api/admin/pricing/refresh` for triggering on-demand refreshes.

## Requirements

### Requirement: Pricing Read Response Shape

`GET /api/pricing` MUST return a JSON body with top-level fields `lastRefreshedAt` (ISO-8601 instant or `null`), `primarySource` (string identifier of the upstream, e.g. `"litellm"`), and `models` (array). Each entry in `models` MUST include `provider`, `model`, `inputTokenPricePerMillion`, `outputTokenPricePerMillion`, `source` (one of `OVERRIDE`, `REMOTE`, `FALLBACK`), and `fetchedAt` (ISO-8601 instant).

#### Scenario: Successful response includes freshness metadata

- GIVEN the application has at least one row in `model_pricing`
- WHEN a client issues `GET /api/pricing`
- THEN the response status MUST be `200`
- AND the body MUST contain `lastRefreshedAt`, `primarySource`, and `models`
- AND every entry in `models` MUST contain `source` and `fetchedAt`

#### Scenario: Per-row source reflects winning layer

- GIVEN `(ANTHROPIC, claude-opus-4-7)` has an override entry
- AND `(OPENAI, gpt-5)` has only a REMOTE row
- AND `(MISTRAL, codestral)` has only a FALLBACK row
- WHEN a client issues `GET /api/pricing`
- THEN the `source` field for `claude-opus-4-7` MUST be `OVERRIDE`
- AND the `source` field for `gpt-5` MUST be `REMOTE`
- AND the `source` field for `codestral` MUST be `FALLBACK`

### Requirement: Pricing Read Resilience

`GET /api/pricing` MUST return `200 OK` even when no remote refresh has ever succeeded, serving the FALLBACK snapshot seeded from `pricing.yaml`. The endpoint MUST NOT return `503` or `500` purely because remote refresh is disabled, has never run, or has only ever failed.

#### Scenario: Cold-start without remote refresh

- GIVEN the application has just booted and seeded FALLBACK rows
- AND no refresh has ever run
- WHEN a client issues `GET /api/pricing`
- THEN the response status MUST be `200`
- AND `lastRefreshedAt` MAY be `null` or the seeding instant
- AND every entry's `source` MUST be `FALLBACK`

#### Scenario: All refreshes have failed

- GIVEN every refresh attempt since boot has failed
- WHEN a client issues `GET /api/pricing`
- THEN the response status MUST be `200`
- AND the body MUST reflect the FALLBACK snapshot

### Requirement: Sorted Model Output

The `models` array in `GET /api/pricing` MUST be sorted ascending by `provider.configKey()` and, within the same provider, ascending by `model` (lexicographic).

#### Scenario: Models sorted by provider then model

- GIVEN `model_pricing` contains entries for providers `OPENAI`, `ANTHROPIC`, and `DEEPSEEK`
- WHEN a client issues `GET /api/pricing`
- THEN the entries MUST appear in order such that `provider.configKey()` is ascending
- AND within each provider group, `model` MUST be ascending lexicographically

### Requirement: Admin Refresh Endpoint

`POST /api/admin/pricing/refresh` MUST trigger an immediate pricing refresh, persist the result transactionally, and return `202 Accepted` with a JSON body containing `lastRefreshedAt` (ISO-8601 instant of completion) and counts `updated`, `skipped`, and `failed` (non-negative integers).

#### Scenario: Successful manual refresh

- GIVEN refresh is enabled (`tokenmeter.pricing.refresh.enabled=true`)
- AND the upstream returns valid pricing for all 17 mapped models
- WHEN a client issues `POST /api/admin/pricing/refresh`
- THEN the response status MUST be `202`
- AND the body MUST contain `lastRefreshedAt` equal to the refresh completion instant
- AND the body MUST contain `updated=17`, `skipped=0`, `failed=0`

#### Scenario: Partial upstream coverage

- GIVEN refresh is enabled
- AND the upstream omits 2 of the 17 mapped models
- WHEN a client issues `POST /api/admin/pricing/refresh`
- THEN the response status MUST be `202`
- AND the body MUST contain `updated=15` and `skipped=2`

#### Scenario: Upstream failure during manual refresh

- GIVEN refresh is enabled
- AND the upstream returns a 5xx response
- WHEN a client issues `POST /api/admin/pricing/refresh`
- THEN the response body MUST contain `failed >= 1`
- AND no row in `model_pricing` MUST be modified

### Requirement: Admin Refresh Disabled State

`POST /api/admin/pricing/refresh` MUST return `503 Service Unavailable` when refresh is disabled via configuration (`tokenmeter.pricing.refresh.enabled=false`).

#### Scenario: Refresh disabled

- GIVEN `tokenmeter.pricing.refresh.enabled=false`
- WHEN a client issues `POST /api/admin/pricing/refresh`
- THEN the response status MUST be `503`
- AND no upstream HTTP call MUST be issued
- AND no row in `model_pricing` MUST be modified
