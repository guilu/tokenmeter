# Analysis Jobs — Main Spec

Source: archived change `observable-analysis-jobs` (2026-05-23) + delta `concurrent-analysis-limits` (2026-05-24). Updated by future changes via additional deltas.

---

# Analysis Jobs Specification

## Purpose

Defines the asynchronous lifecycle of repository analysis requests: how `POST /api/analyze` submits work, how the backend tracks job state, phase, progress and metrics, and how clients observe results via `GET /api/analyze/jobs/{jobId}`. Also pins down retention, crash reconciliation and the client-side progress contract.

## Requirements

### Requirement: Asynchronous Submission Contract

`POST /api/analyze` SHALL create an `AnalysisJob` row and return HTTP `202 Accepted` with a JSON body containing `jobId` (UUID string), `status` (one of `QUEUED`, `RUNNING`), `statusUrl` (absolute or root-relative path resolving to `GET /api/analyze/jobs/{jobId}`), and `analysisId` (always `null` in this capability; reserved for the future fast-path). The synchronous response MUST NOT carry analysis results. The 202 body MUST NOT include `queueState`.

Synchronous failures MUST NOT create a job row:

- An `INVALID_URL` failure MUST return `400 Bad Request` with the existing error envelope.
- A `RATE_LIMITED` failure MUST return `429 Too Many Requests` with the existing error envelope. `RATE_LIMITED` SHALL be reserved for exactly two causes:
  1. The IP rate limit enforced by `AnalyzeRateLimitInterceptor` is exceeded.
  2. The executor's internal queue ceiling (`tokenmeter.analyze-throttle.queue-capacity`) is reached and a new submission cannot be enqueued.

Worker-slot contention (all RUNNING slots busy but queue has capacity) MUST NOT return `429`. It MUST return `202 Accepted` with `status = QUEUED`.

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

### Requirement: Job Status Domain

An `AnalysisJob` SHALL carry exactly one `status` from the closed set `{ QUEUED, RUNNING, SUCCESS, FAILED }`. `QUEUED` and `RUNNING` are non-terminal; `SUCCESS` and `FAILED` are terminal. A terminal job MUST NOT transition to any other status.

#### Scenario: Status set is closed

- GIVEN any persisted `AnalysisJob` row
- WHEN its `status` is read
- THEN the value MUST be one of `QUEUED`, `RUNNING`, `SUCCESS`, `FAILED`

#### Scenario: Terminal status is immutable

- GIVEN an `AnalysisJob` whose `status` is `SUCCESS` or `FAILED`
- WHEN any subsequent attempt is made to mutate its `status`
- THEN the persisted `status` MUST remain unchanged

### Requirement: Job Phase Domain and Transitions

An `AnalysisJob` SHALL carry exactly one `phase` from the closed set `{ QUEUED, CHECKING_CACHE, CLONING_REPOSITORY, SCANNING_FILES, FILTERING_FILES, COUNTING_TOKENS, CALCULATING_COSTS, SAVING_REPORT, COMPLETED, FAILED }`.

Legal transitions:
- On the happy path, phase MUST advance forward only along the order: `QUEUED → CHECKING_CACHE → CLONING_REPOSITORY → SCANNING_FILES → FILTERING_FILES → COUNTING_TOKENS → CALCULATING_COSTS → SAVING_REPORT → COMPLETED`. Skipping intermediate phases forward is permitted (e.g. when a phase is a no-op), but the relative order MUST NOT regress.
- Any non-terminal phase MAY transition directly to `FAILED`.
- `COMPLETED` and `FAILED` are terminal phases and MUST NOT transition further.
- `phase = COMPLETED` SHALL be reachable only together with `status = SUCCESS`.
- `phase = FAILED` SHALL be reachable only together with `status = FAILED`.

#### Scenario: Happy-path phase order is forward-only

- GIVEN a job that completes successfully
- WHEN its phase trail is observed in chronological order
- THEN every consecutive pair `(phaseN, phaseN+1)` MUST respect the canonical order listed above
- AND the final phase MUST be `COMPLETED`

#### Scenario: Phase can jump to FAILED from any non-terminal phase

- GIVEN a job currently in `CLONING_REPOSITORY` (or any non-terminal phase)
- WHEN an unrecoverable error occurs
- THEN the next observed phase MUST be `FAILED`
- AND `status` MUST be `FAILED`

#### Scenario: COMPLETED implies SUCCESS

- GIVEN a job with `phase = COMPLETED`
- WHEN its `status` is read
- THEN `status` MUST equal `SUCCESS`

### Requirement: Progress Invariant

An `AnalysisJob` SHALL expose a `progressPercent` integer in `[0, 100]`. While `status ≠ SUCCESS` OR `analysisId IS NULL`, `progressPercent` MUST be `≤ 99`. `progressPercent = 100` SHALL be reachable only as part of the same transition that sets `status = SUCCESS` AND populates a non-null `analysisId`.

#### Scenario: In-flight job never reports 100

- GIVEN a job whose `status ∈ {QUEUED, RUNNING}`
- WHEN `progressPercent` is read at any time
- THEN the value MUST be `≤ 99`

#### Scenario: SAVING_REPORT phase still caps at 99

- GIVEN a job in `phase = SAVING_REPORT` and `status = RUNNING`
- WHEN `progressPercent` is read
- THEN the value MUST be `≤ 99`

#### Scenario: 100 requires SUCCESS and analysisId

- GIVEN a job that has just transitioned to `status = SUCCESS` with a non-null `analysisId`
- WHEN `progressPercent` is read after that transition
- THEN the value MAY be `100`
- AND no earlier observation of that job MUST have reported `progressPercent = 100`

### Requirement: Job Timestamps

An `AnalysisJob` SHALL record `createdAt` and `updatedAt` (non-null), `startedAt` (nullable until the job leaves `QUEUED`), and `completedAt` (nullable until the job reaches a terminal status). `updatedAt` SHALL be refreshed on every persisted mutation.

#### Scenario: Timestamps reflect lifecycle

- GIVEN a job that transitions `QUEUED → RUNNING → SUCCESS`
- WHEN the job snapshot is read after each transition
- THEN `createdAt` MUST be set on insertion and never change
- AND `startedAt` MUST be non-null once `status` first becomes `RUNNING`
- AND `completedAt` MUST be non-null once `status` becomes `SUCCESS` or `FAILED`
- AND `updatedAt` MUST be `≥` any previously observed `updatedAt`

### Requirement: Failure Payload

A job in `status = FAILED` SHALL record a non-null `errorCode` and a non-null `errorMessage`. `errorCode` SHALL be a stable identifier reusable by the frontend `toUserMessage` mapper (e.g. `CLONE_TIMEOUT`, `REPOSITORY_TOO_LARGE`, `ANALYSIS_FAILED`, `JOB_INTERRUPTED`). `errorMessage` SHALL be human-readable and free of PII or secrets.

#### Scenario: Clone timeout materialises a failed job

- GIVEN a repository whose clone exceeds the configured timeout
- WHEN the job pipeline aborts the clone
- THEN the job MUST reach `status = FAILED` and `phase = FAILED`
- AND `errorCode` MUST be set (e.g. `CLONE_TIMEOUT`)
- AND `errorMessage` MUST be non-empty

#### Scenario: Unexpected exception falls back to generic code

- GIVEN an unexpected exception during `executeJob`
- WHEN the job is marked failed
- THEN `errorCode` MUST be set (default `ANALYSIS_FAILED` when no domain-specific code applies)
- AND `errorMessage` MUST be non-empty

### Requirement: Optional Pipeline Metrics

An `AnalysisJob` MAY expose any subset of the following metrics: `filesDiscovered`, `filesProcessed`, `filesSkipped`, `tokensCounted`, `contextWindows`, `pricingModelsProcessed`. Each metric MUST be either a non-negative integer (or non-negative number for `tokensCounted`) or `null` until the corresponding pipeline phase has emitted a value. A metric, once non-null, MUST NOT regress to `null`.

#### Scenario: Metrics fill in as phases progress

- GIVEN a job currently in `phase = COUNTING_TOKENS`
- WHEN the job snapshot is read
- THEN `filesDiscovered` MUST be non-null
- AND `filesProcessed` MAY be non-null and MUST be `≤ filesDiscovered`
- AND `pricingModelsProcessed` MAY still be `null`

#### Scenario: Metrics are non-negative

- GIVEN any job snapshot
- WHEN any non-null metric is read
- THEN its value MUST be `≥ 0`

### Requirement: Job Observability Endpoint

`GET /api/analyze/jobs/{jobId}` SHALL return `200 OK` with a JSON body describing the current job snapshot: `jobId`, `status`, `phase`, `phaseLabel` (human-readable label for the phase), `progressPercent`, `message` (nullable, short human-readable hint), `analysisId` (nullable), `error` (nullable object with `code` and `message` populated only when `status = FAILED`), `metrics` (nullable object with any subset of the keys defined in *Optional Pipeline Metrics*), `queueState` (nullable object — see *Queue State in Job Snapshot*), and `timestamps` containing `createdAt`, `startedAt?`, `updatedAt`, and `completedAt?` as ISO-8601 instants.

The endpoint SHALL be idempotent and safe to invoke at a polling cadence of approximately 1.5 s without rate-limiting side effects. It SHALL NOT be subject to the `AnalyzeRateLimitInterceptor` that guards `POST /api/analyze`.

A request for a `jobId` that does not exist (or has expired) MUST return `404 Not Found` with the existing error envelope.

The endpoint MUST return `200` even when the underlying job is `FAILED`; HTTP `5xx` MUST NOT be used to surface a job-level failure.

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

### Requirement: Persisted Job State Survives Request Lifecycle

A job's terminal state (`SUCCESS` or `FAILED`) SHALL remain queryable after the originating HTTP request ends, until the configured retention window elapses. Retention defaults SHALL be `7 days` for `SUCCESS` rows and `30 days` for `FAILED` rows, both measured from `completedAt`. Both windows SHALL be configurable. Rows whose retention has elapsed SHALL be removed by a scheduled cleanup task.

#### Scenario: Terminal job stays queryable across requests

- GIVEN a job that reached `status = SUCCESS` 5 minutes ago
- WHEN a client issues `GET /api/analyze/jobs/{jobId}` from a fresh connection
- THEN the response status MUST be `200`
- AND the body MUST reflect the persisted terminal snapshot

#### Scenario: SUCCESS retention default is 7 days

- GIVEN a `SUCCESS` job whose `completedAt` is older than the configured success retention (default 7 days)
- WHEN the scheduled cleanup runs
- THEN the row MUST be removed from `analysis_job`
- AND a subsequent `GET /api/analyze/jobs/{jobId}` MUST return `404`

#### Scenario: FAILED retention default is 30 days

- GIVEN a `FAILED` job whose `completedAt` is older than the configured failure retention (default 30 days)
- WHEN the scheduled cleanup runs
- THEN the row MUST be removed from `analysis_job`
- AND a subsequent `GET /api/analyze/jobs/{jobId}` MUST return `404`

#### Scenario: Retention windows are configurable

- GIVEN configuration overrides the default retention windows
- WHEN the scheduled cleanup runs
- THEN deletion MUST honour the overridden values
- AND no row whose `completedAt` is within the configured window MUST be deleted

### Requirement: Process Restart Reconciles Non-Terminal Jobs

On application start, any `AnalysisJob` whose `status` is non-terminal (`QUEUED` or `RUNNING`) and which is inherited from a previous process SHALL be transitioned to `status = FAILED`, `phase = FAILED`, with `errorCode = JOB_INTERRUPTED` and a non-empty `errorMessage`. `completedAt` MUST be set to the reconciliation instant.

#### Scenario: Crashed-mid-job is reconciled at boot

- GIVEN a row in `analysis_job` with `status = RUNNING` left by a previous process
- WHEN the application starts
- THEN that row MUST be updated to `status = FAILED`, `phase = FAILED`, `errorCode = JOB_INTERRUPTED`
- AND `completedAt` MUST be non-null
- AND a subsequent `GET /api/analyze/jobs/{jobId}` MUST return `200` with that failure payload

#### Scenario: Queued-on-shutdown is reconciled at boot

- GIVEN a row in `analysis_job` with `status = QUEUED` left by a previous process
- WHEN the application starts
- THEN that row MUST be updated to `status = FAILED`, `phase = FAILED`, `errorCode = JOB_INTERRUPTED`

### Requirement: Pre-existing Analysis Endpoints Preserved

The following pre-existing HTTP endpoints SHALL continue to behave as specified prior to this change. This change MUST NOT alter their request shape, response shape, status codes, or rate-limiting behaviour:

- `GET /api/analyze/{id}` — returns a completed analysis by id.
- `GET /api/analyze/{id}/cost-breakdown` — returns the cost breakdown of a completed analysis.
- `GET /api/pricing` — returns the pricing snapshot.
- `POST /api/repositories/intake` — legacy intake endpoint.

#### Scenario: Analysis read endpoint is unchanged

- GIVEN an existing analysis with id `A`
- WHEN a client issues `GET /api/analyze/A`
- THEN the response status, body shape, and content MUST be identical to the pre-change behaviour

#### Scenario: Cost breakdown endpoint is unchanged

- GIVEN an existing analysis with id `A`
- WHEN a client issues `GET /api/analyze/A/cost-breakdown`
- THEN the response status, body shape, and content MUST be identical to the pre-change behaviour

#### Scenario: Pricing endpoint is unchanged

- WHEN a client issues `GET /api/pricing`
- THEN the response status, body shape, and content MUST be identical to the behaviour defined in the `pricing-api` spec

#### Scenario: Legacy intake endpoint is unchanged

- WHEN a client issues `POST /api/repositories/intake`
- THEN the response status, body shape, and content MUST be identical to the pre-change behaviour

### Requirement: Client Progress Invariants

A client that consumes `AnalysisJobResponse` MUST NOT render a progress indicator at `100%` until the most recent snapshot reports `status = SUCCESS` AND `analysisId != null`. The client MUST display the `phase` and `metrics` reported by the backend rather than synthesising fake progress. On `status = SUCCESS` with a non-null `analysisId`, the client SHALL navigate to the analysis result view for that `analysisId`. On `status = FAILED`, the client SHALL surface `error.message` using its existing error-alert styling.

#### Scenario: Client suppresses 100% while RUNNING

- GIVEN the client is polling a job whose latest snapshot is `status = RUNNING`, `progressPercent = 99`
- WHEN the progress UI is rendered
- THEN the displayed progress MUST NOT exceed 99%
- AND no transition to the analysis result view MUST occur

#### Scenario: Client navigates on SUCCESS with analysisId

- GIVEN the client receives a snapshot with `status = SUCCESS` and non-null `analysisId`
- WHEN the client processes that snapshot
- THEN the client MUST stop polling
- AND the client MUST navigate to the analysis view for that `analysisId`

#### Scenario: Client surfaces backend error on FAILED

- GIVEN the client receives a snapshot with `status = FAILED` and a populated `error.message`
- WHEN the client renders the error state
- THEN the user-visible alert MUST contain `error.message`
- AND polling MUST stop

#### Scenario: Client uses backend phase, not a local timer

- GIVEN the client is polling an in-flight job
- WHEN the rendered phase label is observed across two consecutive snapshots
- THEN the displayed phase MUST be derived from `phase` / `phaseLabel` in the latest snapshot
- AND it MUST NOT advance autonomously while `phase` is unchanged on the backend

## Cross-cutting Acceptance Scenarios

The following end-to-end scenarios MUST hold across the requirements above.

#### Scenario: Long analysis remains below 100% until SUCCESS with analysisId

- GIVEN a repository whose pipeline takes longer than 30 seconds
- WHEN the client polls `GET /api/analyze/jobs/{jobId}` continuously
- THEN every snapshot prior to `status = SUCCESS` MUST report `progressPercent ≤ 99`
- AND `progressPercent = 100` MUST appear only in or after the first snapshot where `status = SUCCESS` and `analysisId` is non-null

#### Scenario: Large repository in CALCULATING_COSTS or SAVING_REPORT never reports 100

- GIVEN a job whose current `phase` is `CALCULATING_COSTS` or `SAVING_REPORT`
- WHEN the client polls `GET /api/analyze/jobs/{jobId}`
- THEN the snapshot MUST report `progressPercent ≤ 99`
- AND `analysisId` MUST be `null`

#### Scenario: Clone failure produces a persisted FAILED job

- GIVEN a repository whose clone fails (timeout, network, or auth)
- WHEN the client polls `GET /api/analyze/jobs/{jobId}`
- THEN the eventual snapshot MUST report `status = FAILED`, `phase = FAILED`
- AND `error.code` and `error.message` MUST be non-empty
- AND the same snapshot MUST remain queryable from a fresh request

#### Scenario: Successful submission eventually yields SUCCESS with analysisId

- GIVEN a valid public repository URL submitted via `POST /api/analyze`
- WHEN the client polls `GET /api/analyze/jobs/{jobId}` until a terminal snapshot
- THEN that terminal snapshot MUST report `status = SUCCESS` and a non-null `analysisId`
- AND `GET /api/analyze/{analysisId}` MUST return the corresponding analysis

#### Scenario: Invalid URL does not insert a job row

- GIVEN a request body with a URL that fails parsing
- WHEN a client issues `POST /api/analyze`
- THEN the response status MUST be `400`
- AND no new row MUST exist in `analysis_job` as a result of this request

#### Scenario: Rate-limited submission does not insert a job row

- GIVEN the executor's internal queue has reached `tokenmeter.analyze-throttle.queue-capacity` and every RUNNING slot is busy
- WHEN a client issues `POST /api/analyze` with a valid URL
- THEN the response status MUST be `429`
- AND no new row MUST exist in `analysis_job` as a result of this request

#### Scenario: Polling an unknown jobId returns 404

- GIVEN a `jobId` that has never been issued
- WHEN a client issues `GET /api/analyze/jobs/{jobId}`
- THEN the response status MUST be `404`

#### Scenario: Crashed process restart surfaces previously running jobs as FAILED/JOB_INTERRUPTED

- GIVEN a row in `analysis_job` with `status = RUNNING` left over from a crashed previous process
- WHEN the application restarts and the reconciliation runs
- AND the client issues `GET /api/analyze/jobs/{jobId}` for that row
- THEN the response status MUST be `200`
- AND `status` MUST be `FAILED`
- AND `error.code` MUST be `JOB_INTERRUPTED`

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
