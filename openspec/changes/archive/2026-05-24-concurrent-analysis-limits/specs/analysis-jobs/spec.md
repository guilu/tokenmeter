# Delta for analysis-jobs

Esta delta MODIFICA la capability existente `analysis-jobs` (creada en `2026-05-23-observable-analysis-jobs`). Invierte el contrato de saturación del executor (slot lleno deja de devolver 429) y añade observabilidad de cola al snapshot del job.

## MODIFIED Requirements

### Requirement: Asynchronous Submission Contract

`POST /api/analyze` SHALL create an `AnalysisJob` row and return HTTP `202 Accepted` with a JSON body containing `jobId` (UUID string), `status` (one of `QUEUED`, `RUNNING`), `statusUrl` (absolute or root-relative path resolving to `GET /api/analyze/jobs/{jobId}`), and `analysisId` (always `null` in this capability; reserved for the future fast-path). The synchronous response MUST NOT carry analysis results. The 202 body MUST NOT include `queueState`.

Synchronous failures MUST NOT create a job row:

- An `INVALID_URL` failure MUST return `400 Bad Request` with the existing error envelope.
- A `RATE_LIMITED` failure MUST return `429 Too Many Requests` with the existing error envelope. `RATE_LIMITED` SHALL be reserved for exactly two causes:
  1. The IP rate limit enforced by `AnalyzeRateLimitInterceptor` is exceeded.
  2. The executor's internal queue ceiling (`tokenmeter.analyze-throttle.queue-capacity`) is reached and a new submission cannot be enqueued.

Worker-slot contention (all RUNNING slots busy but queue has capacity) MUST NOT return `429`. It MUST return `202 Accepted` with `status = QUEUED`. (Previously: any saturation of the executor — slot or queue — returned `429 RATE_LIMITED`.)

#### Scenario: Submission of a valid URL creates a queued job

- GIVEN a syntactically valid public GitHub repository URL
- AND the executor has capacity
- WHEN a client issues `POST /api/analyze` with that URL
- THEN the response status MUST be `202`
- AND the body MUST contain non-null `jobId`, `status ∈ {QUEUED, RUNNING}`, `statusUrl`, and `analysisId = null`
- AND the body MUST NOT contain a `queueState` field
- AND exactly one row MUST exist in `analysis_job` with that `jobId`

#### Scenario: Invalid GitHub URL is rejected synchronously

- GIVEN a request body whose URL fails `GitHubRepositoryUrl.parse`
- WHEN a client issues `POST /api/analyze`
- THEN the response status MUST be `400`
- AND the body MUST carry `errorCode = INVALID_URL`
- AND no row MUST be inserted into `analysis_job`

#### Scenario: Worker-slot contention enqueues instead of rejecting

- GIVEN every RUNNING slot of the analysis executor is busy
- AND the executor's internal queue has free capacity
- WHEN a client issues `POST /api/analyze` with a valid URL
- THEN the response status MUST be `202`
- AND the body MUST carry `status = QUEUED`
- AND exactly one row MUST exist in `analysis_job` with that `jobId` and `status = QUEUED`
- AND no response with `429 RATE_LIMITED` MUST be produced by slot contention alone

#### Scenario: Queue ceiling reached rejects synchronously with 429

- GIVEN the executor's internal queue has reached `tokenmeter.analyze-throttle.queue-capacity` and all slots are busy
- WHEN a client issues `POST /api/analyze` with a valid URL
- THEN the response status MUST be `429`
- AND the body MUST carry `error.code = RATE_LIMITED`
- AND `error.message` MUST indicate the analysis queue is at capacity
- AND no new row MUST be inserted into `analysis_job`

### Requirement: Job Observability Endpoint

`GET /api/analyze/jobs/{jobId}` SHALL return `200 OK` with a JSON body describing the current job snapshot: `jobId`, `status`, `phase`, `phaseLabel` (human-readable label for the phase), `progressPercent`, `message` (nullable, short human-readable hint), `analysisId` (nullable), `error` (nullable object with `code` and `message` populated only when `status = FAILED`), `metrics` (nullable object with any subset of the keys defined in *Optional Pipeline Metrics*), `queueState` (nullable object — see *Queue State in Job Snapshot*), and `timestamps` containing `createdAt`, `startedAt?`, `updatedAt`, and `completedAt?` as ISO-8601 instants.

The endpoint SHALL be idempotent and safe to invoke at a polling cadence of approximately 1.5 s without rate-limiting side effects. It SHALL NOT be subject to the `AnalyzeRateLimitInterceptor` that guards `POST /api/analyze`.

A request for a `jobId` that does not exist (or has expired) MUST return `404 Not Found` with the existing error envelope.

The endpoint MUST return `200` even when the underlying job is `FAILED`; HTTP `5xx` MUST NOT be used to surface a job-level failure. (Previously: same shape, without the `queueState` field.)

#### Scenario: Polling a queued job

- GIVEN a job that was just submitted and not yet executed
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN the response status MUST be `200`
- AND `status` MUST be `QUEUED`
- AND `phase` MUST be `QUEUED`
- AND `progressPercent` MUST be `≤ 99`
- AND `analysisId` MUST be `null`
- AND `error` MUST be `null`
- AND `queueState` MUST be a non-null object with `runningCount`, `maxConcurrency`, and `queuePosition`

#### Scenario: Polling a completed job

- GIVEN a job that has reached `status = SUCCESS`
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN the response status MUST be `200`
- AND `status` MUST be `SUCCESS`
- AND `phase` MUST be `COMPLETED`
- AND `analysisId` MUST be a non-null reference to an existing `analysis` row
- AND `progressPercent` MUST be `100`
- AND `error` MUST be `null`
- AND `queueState` MUST be absent or `null`

#### Scenario: Polling a failed job

- GIVEN a job that has reached `status = FAILED`
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN the response status MUST be `200`
- AND `status` MUST be `FAILED`
- AND `phase` MUST be `FAILED`
- AND `error.code` MUST be non-empty
- AND `error.message` MUST be non-empty
- AND `analysisId` MUST be `null`
- AND `queueState` MUST be absent or `null`

#### Scenario: Polling an unknown job

- GIVEN a `jobId` that does not exist (never created or already expired)
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN the response status MUST be `404`

#### Scenario: Polling is not rate-limited

- GIVEN any valid `jobId`
- WHEN a client issues `GET /api/analyze/jobs/{jobId}` repeatedly at ~1.5 s intervals
- THEN every response status MUST be `200` or `404`
- AND no response MUST be `429` due to the analyze rate limiter

## ADDED Requirements

### Requirement: Concurrency Cap and Queue Promotion

At any wall-clock instant the count of rows in `analysis_job` with `status = RUNNING` SHALL be `≤ tokenmeter.analyze-throttle.max-concurrent` (`maxConcurrency`, integer `≥ 1`). When a job in `status = RUNNING` reaches a terminal status (`SUCCESS` or `FAILED`), the next `QUEUED` job — selected FIFO by `created_at ASC` with `id ASC` as tiebreaker — SHALL transition to `status = RUNNING` within the executor's next dispatch tick.

Promotion MUST NOT depend on the outcome of the previous job: a `FAILED` RUNNING release a worker slot in the same way as a `SUCCESS` release does. No QUEUED row MAY remain stuck behind a failure.

#### Scenario: Concurrency cap is never exceeded

- GIVEN `maxConcurrency = 2`
- AND two jobs are already in `status = RUNNING`
- WHEN a third `POST /api/analyze` is submitted with a valid URL
- THEN the response MUST be `202` with `status = QUEUED`
- AND the count of rows with `status = RUNNING` MUST remain `≤ 2` until one of the RUNNING jobs reaches a terminal status
- AND a follow-up `GET /api/analyze/jobs/{jobId}` MUST report `queueState.queuePosition = 1`

#### Scenario: SUCCESS releases the next queued job

- GIVEN `maxConcurrency = 2`
- AND two jobs are RUNNING and three jobs are QUEUED (in FIFO order Q1, Q2, Q3)
- WHEN one RUNNING job transitions to `status = SUCCESS`
- THEN within the executor's next dispatch tick, Q1 MUST transition to `status = RUNNING`
- AND Q2 MUST report `queueState.queuePosition = 1`
- AND Q3 MUST report `queueState.queuePosition = 2`

#### Scenario: FAILED RUNNING does not block the queue

- GIVEN `maxConcurrency = 2`
- AND two jobs are RUNNING and three jobs are QUEUED
- WHEN one RUNNING job transitions to `status = FAILED`
- THEN within the executor's next dispatch tick, the FIFO-next QUEUED job MUST transition to `status = RUNNING`
- AND the count of RUNNING jobs MUST remain `≤ maxConcurrency`

### Requirement: Queue State in Job Snapshot

`GET /api/analyze/jobs/{jobId}` SHALL include an optional `queueState` object with the following fields:

- `runningCount`: integer `≥ 0` — current count of rows in `analysis_job` with `status = RUNNING`.
- `maxConcurrency`: integer `≥ 1` — system-wide cap read from `tokenmeter.analyze-throttle.max-concurrent`.
- `queuePosition`: integer `≥ 1` — 1-based position of this job among all `QUEUED` rows sorted by `created_at ASC, id ASC`. MUST be present only when `status = QUEUED`. The position is an estimate and MAY vary between polls; it MUST monotonically decrease in the absence of new submissions ahead of the job.

`queueState` MUST be omitted (or `null`) when `status ∈ {SUCCESS, FAILED}`. `queueState` MUST NOT appear in the `POST /api/analyze` 202 response body. When `status = RUNNING`, `queueState.queuePosition` MUST be absent or `null` but `runningCount` and `maxConcurrency` MUST be present.

#### Scenario: Queued snapshot includes full queue state

- GIVEN a job with `status = QUEUED`
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN the response body MUST contain `queueState.runningCount ≥ 0`
- AND `queueState.maxConcurrency ≥ 1`
- AND `queueState.queuePosition ≥ 1`

#### Scenario: Running snapshot omits queuePosition

- GIVEN a job with `status = RUNNING`
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN the response body MUST contain `queueState.runningCount` and `queueState.maxConcurrency`
- AND `queueState.queuePosition` MUST be absent or `null`

#### Scenario: Terminal snapshot omits queue state

- GIVEN a job with `status = SUCCESS` or `status = FAILED`
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN the response body MUST NOT include `queueState` (or it MUST be `null`)

### Requirement: Queued Phase Labelling

When `status = QUEUED` AND `queueState.runningCount ≥ queueState.maxConcurrency`, `phaseLabel` SHALL be the literal string `"Waiting for an analysis slot"`. Otherwise, when `status = QUEUED` and slots are available (job is awaiting executor pickup), `phaseLabel` SHALL be the previously defined queued label (`"Queued"`). The label MUST be computed by the backend mapper from `queueState`, not synthesised by the client.

#### Scenario: Label switches to "Waiting for an analysis slot" under contention

- GIVEN a job with `status = QUEUED`
- AND `queueState.runningCount ≥ queueState.maxConcurrency`
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN `phaseLabel` MUST equal `"Waiting for an analysis slot"`

#### Scenario: Label stays "Queued" without contention

- GIVEN a job with `status = QUEUED`
- AND `queueState.runningCount < queueState.maxConcurrency` (e.g. brief gap before executor pickup)
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN `phaseLabel` MUST equal `"Queued"`

### Requirement: Configurable Queue Ceiling

`tokenmeter.analyze-throttle.queue-capacity` SHALL bound the executor's internal queue. Default `256`, minimum `1`. When the executor cannot enqueue a submitted task because this ceiling has been reached, the submission MUST return `429 Too Many Requests` with `error.code = RATE_LIMITED` and an `error.message` that indicates the analysis queue is at capacity. No row in `analysis_job` MUST be persisted in that case (any speculative insert MUST be rolled back).

#### Scenario: Submission beyond queue ceiling returns 429 and persists no row

- GIVEN `queue-capacity = 256`
- AND there are `256` tasks already enqueued in the executor with every RUNNING slot busy
- WHEN a client issues `POST /api/analyze` with a valid URL
- THEN the response status MUST be `429`
- AND `error.code` MUST equal `RATE_LIMITED`
- AND the count of rows in `analysis_job` MUST NOT increase as a result of this request

#### Scenario: Default queue-capacity is 256

- GIVEN no override for `tokenmeter.analyze-throttle.queue-capacity` in configuration
- WHEN the application starts
- THEN the effective queue capacity MUST be `256`

## Cross-cutting Acceptance Scenarios

The following end-to-end scenarios MUST hold across the requirements above and existing requirements.

#### Scenario: Third submission with maxConcurrency=2 enqueues at position 1

- GIVEN `maxConcurrency = 2`
- AND two jobs are RUNNING
- WHEN a third valid `POST /api/analyze` is issued
- THEN the response MUST be `202` with `status = QUEUED`
- AND the first subsequent `GET /api/analyze/jobs/{jobId}` MUST report `queueState.queuePosition = 1`
- AND `phaseLabel` MUST equal `"Waiting for an analysis slot"`

#### Scenario: SUCCESS of head of line promotes FIFO-next

- GIVEN `maxConcurrency = 2`, 2 RUNNING and 3 QUEUED (Q1, Q2, Q3 by FIFO)
- WHEN one RUNNING transitions to `SUCCESS`
- THEN Q1 MUST become `RUNNING` within the executor's next dispatch tick

#### Scenario: FAILED of head of line does not stall the queue

- GIVEN `maxConcurrency = 2`, 2 RUNNING and 3 QUEUED (Q1, Q2, Q3 by FIFO)
- WHEN one RUNNING transitions to `FAILED`
- THEN Q1 MUST become `RUNNING` within the executor's next dispatch tick

#### Scenario: 257th submission against a full queue returns 429 with no row added

- GIVEN `queue-capacity = 256`, every slot busy and `256` tasks enqueued
- WHEN a `257`th valid `POST /api/analyze` is issued
- THEN the response MUST be `429` with `error.code = RATE_LIMITED`
- AND the total count of rows in `analysis_job` MUST NOT increase

#### Scenario: QUEUED snapshot exposes runningCount, maxConcurrency, queuePosition

- GIVEN a job with `status = QUEUED`
- WHEN polling `GET /api/analyze/jobs/{jobId}`
- THEN the response MUST include `queueState.runningCount`, `queueState.maxConcurrency`, and `queueState.queuePosition ≥ 1`

#### Scenario: RUNNING snapshot exposes runningCount and maxConcurrency but not queuePosition

- GIVEN a job with `status = RUNNING`
- WHEN polling `GET /api/analyze/jobs/{jobId}`
- THEN the response MUST include `queueState.runningCount` and `queueState.maxConcurrency`
- AND `queueState.queuePosition` MUST be absent or `null`

#### Scenario: SUCCESS snapshot omits queueState entirely

- GIVEN a job with `status = SUCCESS`
- WHEN polling `GET /api/analyze/jobs/{jobId}`
- THEN the response MUST NOT include `queueState` (or it MUST be `null`)

#### Scenario: QUEUED with no slot contention keeps standard label

- GIVEN a job with `status = QUEUED`
- AND `queueState.runningCount < queueState.maxConcurrency` (slot is free, awaiting executor pickup)
- WHEN polling `GET /api/analyze/jobs/{jobId}`
- THEN `phaseLabel` MUST equal `"Queued"` and MUST NOT equal `"Waiting for an analysis slot"`
