# Delta for Pricing

## ADDED Requirements

### Requirement: Pricing Snapshot Identity

The system MUST attach a deterministic, content-derived identifier to the active pricing snapshot. The identifier SHALL be the string `v1:` followed by the lowercase hexadecimal SHA-256 digest of a canonical UTF-8 serialisation of the active snapshot. The canonical input MUST be produced by sorting all `(provider, model)` entries ascending by `provider.configKey()` then by `model.toLowerCase(Locale.ROOT).trim()`, and emitting for each entry the tab-separated tuple `configKey \t lowercaseTrimmedModel \t inputPricePerMillion.setScale(6, HALF_UP).toPlainString() \t outputPricePerMillion.setScale(6, HALF_UP).toPlainString() \t source.name()` followed by `\n`. `externalModelId` and `fetchedAt` MUST be excluded from the canonical input.

The identifier MUST fit within `VARCHAR(80)` so that v1 ids (`v1:` + 64 hex chars = 67 chars) leave room for future prefixes.

#### Scenario: Identical prices yield identical ids regardless of fetchedAt

- GIVEN two pricing snapshots containing the same `(provider, model, inputPricePerMillion, outputPricePerMillion, source)` set
- AND each row's `fetchedAt` differs between the two snapshots
- WHEN the identifier is computed for both
- THEN both identifiers MUST be equal byte-for-byte

#### Scenario: A price change produces a different id

- GIVEN snapshot S1 with `(OPENAI, gpt-5)` priced at `5.000000 / 15.000000` per million
- AND snapshot S2 identical to S1 except `(OPENAI, gpt-5)` priced at `5.500000 / 15.000000`
- WHEN the identifier is computed for both
- THEN the identifier of S1 MUST NOT equal the identifier of S2

#### Scenario: A source change produces a different id

- GIVEN snapshot S1 in which `(OPENAI, gpt-5)` has `source = FALLBACK`
- AND snapshot S2 identical in prices but `(OPENAI, gpt-5)` has `source = REMOTE`
- WHEN the identifier is computed for both
- THEN the identifiers MUST differ

#### Scenario: Identifier carries version prefix

- GIVEN any computed identifier
- WHEN its value is inspected
- THEN it MUST start with the literal prefix `v1:`
- AND the suffix MUST be exactly 64 lowercase hexadecimal characters

### Requirement: Pricing Snapshot Identity Cached Per Refresh

The system MUST expose the current snapshot's identifier alongside the active snapshot list, computed at most once per refresh cycle. A `PricingRefreshedEvent` MUST invalidate the cached identifier so that the next read recomputes from the post-refresh snapshot.

#### Scenario: Identifier is stable across reads with no refresh

- GIVEN the active pricing snapshot has not changed since the last refresh
- WHEN the identifier is queried twice
- THEN both queries MUST return the same identifier
- AND the second query MUST NOT recompute the SHA-256 digest

#### Scenario: PricingRefreshedEvent invalidates the cached identifier

- GIVEN the identifier has been cached for the active snapshot
- WHEN a `PricingRefreshedEvent` is published and the new snapshot has at least one different price
- THEN the next identifier read MUST recompute over the post-refresh snapshot
- AND the returned identifier MUST differ from the pre-refresh identifier

#### Scenario: Cold-start identifier reflects FALLBACK seed

- GIVEN the application has just booted with only FALLBACK rows seeded from `pricing.yaml`
- AND no refresh has run
- WHEN the identifier is queried
- THEN the response MUST be a well-formed `v1:` identifier derived from the FALLBACK rows
