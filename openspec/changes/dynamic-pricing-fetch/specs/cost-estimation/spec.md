# Cost Estimation Specification

## Purpose

Defines the externally observable behaviour of `RepositoryCostEstimationService` and adjacent cost computation logic, ensuring the switch from a static YAML pricing source to a dynamic pricing pipeline is behaviourally transparent. This spec is a compatibility contract: cost computation MUST remain stable; only the pricing source changes.

## Requirements

### Requirement: Pricing-Input Determinism

`RepositoryCostEstimationService` MUST produce identical `ModelCostEstimate` results given identical `ModelPricing` inputs, regardless of whether those inputs originated from `OVERRIDE`, `REMOTE`, or `FALLBACK` sources.

#### Scenario: Same prices yield same estimates across sources

- GIVEN `RepositoryCostEstimationService.estimate(baseTokens)` is invoked with `baseTokens=100_000`
- AND the pricing provider returns the same `(provider, model, inputPrice, outputPrice)` set in two separate runs, once with `source=REMOTE` and once with `source=FALLBACK`
- WHEN both estimations complete
- THEN the resulting `ModelCostEstimate` collections MUST be element-wise equal in `provider`, `model`, `mode`, and computed `cost`

### Requirement: Live Pricing Snapshot

Cost estimation MUST iterate the pricing snapshot returned by `PricingProvider.all()` (or equivalent contract method) on each invocation. The service MUST NOT cache `ModelPricing` instances across estimation calls in a way that survives a `PricingRefreshedEvent`.

#### Scenario: Refresh between estimations is observed

- GIVEN `RepositoryCostEstimationService` has completed one estimation against snapshot S1
- AND a `PricingRefreshedEvent` is published producing snapshot S2 with different prices for at least one model
- WHEN a second estimation is invoked
- THEN the estimates for the changed model MUST reflect the prices from S2

#### Scenario: Mid-analysis consistency

- GIVEN `RepositoryCostEstimationService.estimate(baseTokens)` is in progress
- AND a refresh transaction commits during that estimation
- WHEN the estimation finishes
- THEN every estimate produced in that single call MUST originate from one consistent snapshot (either fully pre-refresh or fully post-refresh, never a mix)

### Requirement: Cost Computation Formula Preserved

The cost formula `cost = (tokens × multiplier × pricePerMillion) / 1_000_000` and rounding (`HALF_UP` to 6 decimal places) MUST remain unchanged.

#### Scenario: Rounding is HALF_UP at six decimals

- GIVEN a model with `outputTokenPricePerMillion = 3.000000`
- AND `baseTokens = 100_000`
- AND `mode = RAW` (output multiplier = 1, input multiplier = 0)
- WHEN the estimate is computed
- THEN the cost MUST equal `0.300000` (USD) rounded HALF_UP at six decimal places

#### Scenario: Sub-cent precision retained

- GIVEN a model with `outputTokenPricePerMillion = 0.150000` and `inputTokenPricePerMillion = 0.030000`
- AND `baseTokens = 1`
- AND `mode = ASSISTED` (output ×5, input ×1)
- WHEN the estimate is computed
- THEN the cost MUST equal `(1 × 5 × 0.150000 + 1 × 1 × 0.030000) / 1_000_000` rounded HALF_UP at six decimals

### Requirement: Cost Estimation Modes Preserved

The `CostEstimationMode` multipliers MUST remain: `RAW` (output ×1, input ×0), `ASSISTED` (output ×5, input ×1), `AGENTIC` (output ×20, input ×4). Adding, removing, or renumbering modes is out of scope for this change.

#### Scenario: Mode multipliers unchanged

- GIVEN `CostEstimationMode` enum values are read
- WHEN their multipliers are inspected
- THEN `RAW` MUST report output=1 and input=0
- AND `ASSISTED` MUST report output=5 and input=1
- AND `AGENTIC` MUST report output=20 and input=4

### Requirement: Estimate Cardinality

For each analysis, `RepositoryCostEstimationService` MUST produce exactly `N × 3` estimates where `N` is the number of models returned by the active pricing snapshot and `3` is the number of `CostEstimationMode` values.

#### Scenario: Seventeen-model snapshot yields 51 estimates

- GIVEN the active pricing snapshot contains exactly 17 models
- WHEN `RepositoryCostEstimationService.estimate(baseTokens)` is invoked
- THEN the resulting collection MUST contain exactly 51 `ModelCostEstimate` entries (17 models × 3 modes)
