# Delta for Cost Estimation

## ADDED Requirements

### Requirement: Cost Estimation Captures Pricing Snapshot Identity Atomically

Each invocation of cost estimation MUST capture the active pricing snapshot together with its identifier exactly once, before producing any `ModelCostEstimate`. Every estimate produced by that invocation MUST be computed against that captured snapshot. A `PricingRefreshedEvent` arriving after capture MUST NOT influence the estimates produced by the in-flight invocation, and MUST NOT change the identifier associated with the invocation's persisted outputs.

The capture MUST be exposed to callers (e.g. the analysis job worker) so that the same identifier can be persisted on both the originating job and the resulting analysis.

#### Scenario: Mid-estimation refresh does not affect the in-flight invocation

- GIVEN a cost estimation invocation has captured snapshot S1 with identifier I1
- AND a `PricingRefreshedEvent` commits snapshot S2 with identifier I2 (`I2 != I1`) during the invocation
- WHEN the invocation completes
- THEN every produced `ModelCostEstimate` MUST originate from S1
- AND the identifier associated with this invocation's persisted outputs MUST be I1

#### Scenario: Two back-to-back estimations on the same cached snapshot share an identifier

- GIVEN no `PricingRefreshedEvent` is published between two invocations
- WHEN both invocations capture the active snapshot
- THEN both invocations MUST be associated with the same identifier

#### Scenario: Estimation after refresh uses the new identifier

- GIVEN snapshot S1 with identifier I1 was captured by a prior invocation
- AND a `PricingRefreshedEvent` has since committed snapshot S2 with identifier I2
- WHEN a new estimation invocation captures the active snapshot
- THEN the invocation MUST be associated with identifier I2

## MODIFIED Requirements

### Requirement: Live Pricing Snapshot

Cost estimation MUST iterate the pricing snapshot returned by `PricingProvider.all()` (or equivalent contract method). Within a single invocation, the service MUST capture the snapshot exactly once and use it for every estimate it produces; a `PricingRefreshedEvent` published during that invocation MUST NOT be observed by it. The service MUST NOT cache `ModelPricing` instances across invocations in a way that survives a `PricingRefreshedEvent`.

(Previously: the service had to iterate `PricingProvider.all()` on each invocation and could not cache across `PricingRefreshedEvent`; this delta clarifies that within a single invocation the captured snapshot is frozen and additionally couples that frozen snapshot with the identifier defined in the `pricing` spec.)

#### Scenario: Refresh between estimations is observed

- GIVEN cost estimation has completed one estimation against snapshot S1
- AND a `PricingRefreshedEvent` is published producing snapshot S2 with different prices for at least one model
- WHEN a second estimation is invoked
- THEN the estimates for the changed model MUST reflect the prices from S2
- AND the captured identifier for the second estimation MUST be the identifier of S2

#### Scenario: Mid-analysis consistency

- GIVEN a cost estimation invocation is in progress having captured snapshot S1
- AND a refresh transaction commits during that invocation
- WHEN the invocation finishes
- THEN every estimate produced in that single call MUST originate from S1
- AND none of the produced estimates MUST reflect post-refresh prices
