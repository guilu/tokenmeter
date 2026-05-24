# Delta for Analysis Jobs

## ADDED Requirements

### Requirement: Job and Analysis Carry Pricing Snapshot Identity

When an `AnalysisJob` reaches the `CALCULATING_COSTS` phase, the worker SHALL capture the active pricing snapshot identifier exactly once and persist it on the `analysis_job` row as `pricingSnapshotId`. The same identifier MUST be persisted on the resulting `analysis` row produced by that job. For any successfully completed job, `analysis_job.pricingSnapshotId` MUST equal `analysis.pricingSnapshotId`.

`pricingSnapshotId` MUST be a string of the form `v1:` + 64 lowercase hexadecimal characters, derived per the `pricing` spec's *Pricing Snapshot Identity* requirement. The column SHALL be `VARCHAR(80)` nullable to permit legacy rows.

A job that fails before entering `CALCULATING_COSTS` MUST leave `analysis_job.pricingSnapshotId` as `NULL`. A job that fails during or after `CALCULATING_COSTS` MAY have a non-null `pricingSnapshotId` on `analysis_job` even though no `analysis` row is produced.

Pre-existing rows (inserted before this change) MUST be allowed to have `NULL` `pricingSnapshotId`. The system MUST NOT backfill historical rows.

#### Scenario: Successful job persists identifier on job and analysis

- GIVEN a job that completes successfully against pricing snapshot with identifier `I`
- WHEN the job reaches `status = SUCCESS`
- THEN `analysis_job.pricingSnapshotId` for that job MUST equal `I`
- AND `analysis.pricingSnapshotId` for the resulting analysis MUST equal `I`

#### Scenario: Identifier is captured atomically at start of CALCULATING_COSTS

- GIVEN a job that enters `CALCULATING_COSTS` while the active pricing snapshot identifier is `I1`
- AND a `PricingRefreshedEvent` publishes identifier `I2 != I1` while the job is in `CALCULATING_COSTS`
- WHEN the job completes successfully
- THEN both `analysis_job.pricingSnapshotId` and `analysis.pricingSnapshotId` MUST equal `I1`
- AND no other identifier MUST be observed on either row

#### Scenario: Pre-CALCULATING_COSTS failure leaves snapshot id null

- GIVEN a job that fails in `CLONING_REPOSITORY` (or any phase strictly before `CALCULATING_COSTS`)
- WHEN the job reaches `status = FAILED`
- THEN `analysis_job.pricingSnapshotId` MUST be `NULL`

#### Scenario: Legacy rows without identifier remain readable

- GIVEN a row in `analysis_job` or `analysis` inserted before this change with `pricingSnapshotId = NULL`
- WHEN the row is read through the JPA repository
- THEN the read MUST succeed
- AND the in-memory value MUST be `NULL`

### Requirement: Pricing Block in Analysis Read Endpoints

`GET /api/analyze/{id}` and `GET /api/analyze/{id}/cost-breakdown` MUST include an optional nested `pricing` object with the fields `snapshotId` (string), `primarySource` (string identifier matching the pricing API's `primarySource`), and `capturedAt` (ISO-8601 instant). The `pricing` block MUST be present when the underlying `analysis.pricingSnapshotId` is non-null and MUST be entirely omitted (key absent) when `analysis.pricingSnapshotId IS NULL`.

`capturedAt` SHALL be the instant at which the worker captured the snapshot handle at the start of `CALCULATING_COSTS`. `primarySource` SHALL be derived from the pricing source layer that won at the time of capture (`OVERRIDE`, `REMOTE`, or `FALLBACK`) and SHALL match the pricing-api `primarySource` semantics.

Other fields of these responses MUST remain unchanged in shape, ordering, and meaning.

#### Scenario: Analysis read exposes pricing block when snapshot id is present

- GIVEN an analysis with non-null `pricingSnapshotId` `I` captured at instant `T` against `primarySource = REMOTE`
- WHEN a client issues `GET /api/analyze/{id}`
- THEN the response status MUST be `200`
- AND the body MUST contain a `pricing` object with `snapshotId = I`, `primarySource = "REMOTE"`, and `capturedAt = T`

#### Scenario: Analysis read omits pricing block for legacy rows

- GIVEN an analysis with `pricingSnapshotId IS NULL`
- WHEN a client issues `GET /api/analyze/{id}`
- THEN the response status MUST be `200`
- AND the body MUST NOT contain a `pricing` key (the key is absent, not `null`)

#### Scenario: Cost breakdown exposes the same pricing block

- GIVEN an analysis with non-null `pricingSnapshotId` `I`
- WHEN a client issues `GET /api/analyze/{id}/cost-breakdown`
- THEN the response body MUST contain a `pricing` object with `snapshotId = I` matching the value returned by `GET /api/analyze/{id}`

#### Scenario: Cost breakdown omits pricing block for legacy rows

- GIVEN an analysis with `pricingSnapshotId IS NULL`
- WHEN a client issues `GET /api/analyze/{id}/cost-breakdown`
- THEN the body MUST NOT contain a `pricing` key

### Requirement: Pricing Block in Job Status Snapshot

`GET /api/analyze/jobs/{jobId}` MUST include an optional nested `pricing` object with the fields `snapshotId`, `primarySource`, and `capturedAt`, populated from `analysis_job.pricingSnapshotId` and the captured-at instant. The `pricing` block MUST be present when `analysis_job.pricingSnapshotId` is non-null and MUST be omitted entirely otherwise (including for jobs still in `QUEUED`, `CHECKING_CACHE`, `CLONING_REPOSITORY`, `SCANNING_FILES`, `FILTERING_FILES`, `COUNTING_TOKENS`, and for jobs that failed before `CALCULATING_COSTS`).

The presence of the `pricing` block MUST NOT alter the existing fields, status codes, or rate-limiting behaviour of `GET /api/analyze/jobs/{jobId}`.

#### Scenario: Pricing block appears once the worker enters CALCULATING_COSTS

- GIVEN a job that has reached `phase = CALCULATING_COSTS` and persisted snapshot identifier `I`
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN the body MUST include `pricing.snapshotId = I`
- AND the body MUST include `pricing.primarySource` and `pricing.capturedAt`

#### Scenario: Pricing block is absent for pre-cost phases

- GIVEN a job whose current `phase` is one of `QUEUED`, `CHECKING_CACHE`, `CLONING_REPOSITORY`, `SCANNING_FILES`, `FILTERING_FILES`, or `COUNTING_TOKENS`
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN the body MUST NOT include a `pricing` key

#### Scenario: Pricing block survives terminal SUCCESS

- GIVEN a job that has reached `status = SUCCESS` with `analysisId` non-null and `pricingSnapshotId = I`
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN the body MUST include `pricing.snapshotId = I`

#### Scenario: Pricing block is absent when job failed before CALCULATING_COSTS

- GIVEN a job that reached `status = FAILED` while in `phase = CLONING_REPOSITORY`
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN the body MUST NOT include a `pricing` key

### Requirement: Frontend Renders Pricing Footer When Present

The frontend results page (the view rendered after navigating to `/analyses/{analysisId}`) MUST render a single-line footer near the existing "Analysis id: ... · &lt;date&gt;" line in the format `Pricing: <primarySource> · captured <capturedAt>` when the API response for that analysis contains a `pricing` block. When the `pricing` block is absent, the footer MUST NOT be rendered (no placeholder, no "unknown" fallback text).

The `capturedAt` instant SHALL be formatted using the same date-formatting helper used for the `Analysis id` line. The `primarySource` SHALL be rendered as returned by the API (e.g. `REMOTE`, `FALLBACK`, `OVERRIDE`).

#### Scenario: Footer renders when pricing block is present

- GIVEN the API response for an analysis contains `pricing.primarySource = "REMOTE"` and `pricing.capturedAt = "2026-05-24T10:00:00Z"`
- WHEN the results page is rendered for that analysis
- THEN the rendered DOM MUST contain a line matching `Pricing: REMOTE · captured <formatted-date>`
- AND this line MUST appear near the `Analysis id: ...` line of the page footer

#### Scenario: Footer is omitted when pricing block is absent

- GIVEN the API response for an analysis does not contain a `pricing` key
- WHEN the results page is rendered for that analysis
- THEN the rendered DOM MUST NOT contain a `Pricing:` footer line
- AND no fallback placeholder text MUST be rendered in its place

## MODIFIED Requirements

### Requirement: Pre-existing Analysis Endpoints Preserved

The following pre-existing HTTP endpoints SHALL continue to behave as specified prior to this change with respect to status codes, rate-limiting, and the set of previously documented fields. This change extends `GET /api/analyze/{id}` and `GET /api/analyze/{id}/cost-breakdown` with an OPTIONAL nested `pricing` object per the *Pricing Block in Analysis Read Endpoints* requirement; all other aspects of those responses MUST remain unchanged. `GET /api/pricing` and `POST /api/repositories/intake` MUST remain entirely unchanged.

- `GET /api/analyze/{id}` — returns a completed analysis by id, now optionally including a nested `pricing` block.
- `GET /api/analyze/{id}/cost-breakdown` — returns the cost breakdown of a completed analysis, now optionally including a nested `pricing` block.
- `GET /api/pricing` — returns the pricing snapshot.
- `POST /api/repositories/intake` — legacy intake endpoint.

(Previously: this requirement asserted that the analysis read endpoint and cost-breakdown endpoint MUST remain identical in body shape to the pre-change behaviour. This delta narrowly relaxes that constraint to allow an additive, optional `pricing` block while keeping every other field byte-stable.)

#### Scenario: Analysis read endpoint stable except for optional pricing block

- GIVEN an existing analysis with id `A` whose `pricingSnapshotId IS NULL`
- WHEN a client issues `GET /api/analyze/A`
- THEN the response status, body shape, and content MUST be identical to the pre-change behaviour
- AND no `pricing` key MUST appear in the body

#### Scenario: Cost breakdown endpoint stable except for optional pricing block

- GIVEN an existing analysis with id `A` whose `pricingSnapshotId IS NULL`
- WHEN a client issues `GET /api/analyze/A/cost-breakdown`
- THEN the response status, body shape, and content MUST be identical to the pre-change behaviour
- AND no `pricing` key MUST appear in the body

#### Scenario: Pricing endpoint is unchanged

- WHEN a client issues `GET /api/pricing`
- THEN the response status, body shape, and content MUST be identical to the behaviour defined in the `pricing-api` spec

#### Scenario: Legacy intake endpoint is unchanged

- WHEN a client issues `POST /api/repositories/intake`
- THEN the response status, body shape, and content MUST be identical to the pre-change behaviour
