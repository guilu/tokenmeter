# Archive Report: concurrent-analysis-limits

Archived: 2026-05-24

## Outcome

- Verify status: `pass` (`verify-report.md` in this folder, verdict PASS — 6/6 requirements, 27/27 acceptance scenarios COMPLIANT, all four gates green).
- Worker-slot contention no longer surfaces as `429`: only the executor's internal queue ceiling (`tokenmeter.analyze-throttle.queue-capacity`, default `256`) returns `RATE_LIMITED`. Slot saturation enqueues with `202 + status=QUEUED`.
- `GET /api/analyze/jobs/{jobId}` now exposes a `queueState` snapshot (`runningCount`, `maxConcurrency`, `queuePosition`) computed on-read; queued jobs under contention get `phaseLabel = "Waiting for an analysis slot"`.
- FIFO promotion is guaranteed regardless of outcome: both `SUCCESS` and `FAILED` of a RUNNING job release the slot for the head-of-line `QUEUED` row.
- Recommended by `sdd-verify` with disposition `ARCHIVE`.

## Merged Specs

| Delta path | Main spec path | Action |
|---|---|---|
| `openspec/changes/concurrent-analysis-limits/specs/analysis-jobs/spec.md` | `openspec/specs/analysis-jobs/spec.md` | 2 MODIFIED + 4 ADDED requirements + cross-cutting append |

Modified requirements (replaced in place):

1. `Asynchronous Submission Contract` — narrows `RATE_LIMITED` to IP throttle + queue ceiling only; slot contention now MUST return `202 QUEUED`; 202 body MUST NOT include `queueState`. Adds scenarios `Worker-slot contention enqueues instead of rejecting` and `Queue ceiling reached rejects synchronously with 429`; removes obsolete `Saturated executor rejects synchronously`.
2. `Job Observability Endpoint` — adds `queueState` to the snapshot shape and to the three polling scenarios (queued exposes full state; success/failed assert queueState absent).

Added requirements:

3. `Concurrency Cap and Queue Promotion` — `RUNNING ≤ maxConcurrency`, FIFO promotion by `(created_at ASC, id ASC)`, `FAILED` releases slot like `SUCCESS`.
4. `Queue State in Job Snapshot` — schema for the `queueState` object and presence/absence rules per status.
5. `Queued Phase Labelling` — backend-computed `"Waiting for an analysis slot"` when `runningCount ≥ maxConcurrency`, otherwise `"Queued"`.
6. `Configurable Queue Ceiling` — `tokenmeter.analyze-throttle.queue-capacity` default `256`, minimum `1`, rolls back any speculative insert on rejection.

Cross-cutting scenarios appended (8): third submission with `maxConcurrency=2` enqueues at position 1; SUCCESS / FAILED of head-of-line promotes FIFO-next; 257th submission against a full queue returns 429 with no row added; QUEUED / RUNNING / SUCCESS snapshot shape assertions; QUEUED with no slot contention keeps `"Queued"` label. Existing `Rate-limited submission does not insert a job row` reworded to reflect the queue-ceiling-only semantics.

No destructive deltas (no removals beyond the superseded `Saturated executor rejects synchronously` scenario, replaced by the two new ones). No Flyway baseline changes (V7 is additive). Per `openspec/config.yaml` rules.archive.

## Summary

- Inverts the executor saturation contract: slot-full no longer returns `429`; only the queue ceiling does.
- Adds a `queueState` snapshot (`runningCount`, `maxConcurrency`, `queuePosition`) computed on-read by `AnalysisJobQueryService.getView`.
- Backend mapper now emits `phaseLabel = "Waiting for an analysis slot"` under slot contention; standard `"Queued"` label preserved when slots are free.
- Default `queue-capacity` bumped from `32` to `256`; `AsyncExecutionConfig` keeps `AbortPolicy` so the ceiling materialises as `429 RATE_LIMITED` with no row persisted.
- Frontend `useAnalysisJob` propagates `queueState`; `DashboardPage > LoadingState` renders `Position N · R/M running` while queued.
- Adds Flyway V7 composite index `idx_analysis_job_status_created_at` on `(status, created_at)` to back both `countByStatus` and `countQueuedAheadOf`.

## Commits

Gitmoji + Conventional Commits introduced by this change (oldest first, range `f8c7822..bd15831`):

1. `f8c7822` ✨ feat(jobs): add AnalysisJobQueueState and AnalysisJobView domain types
2. `93de3cd` 🗃️ db(jobs): add V7 composite index and queue-aware repository methods
3. `51ff5ec` ✨ feat(jobs): expose runningCount, queuePosition and maxConcurrency via getView
4. `cb27c67` ✨ feat(api): expose queueState in job snapshot and override phaseLabel under contention
5. `56f0642` 🧪 test(jobs): pin default queueCapacity=256 on analysisJobExecutor bean
6. `9e613f5` 🧪 test(jobs): rename submission saturation test to queue-ceiling semantics
7. `5842c31` 🧪 test(jobs): integration coverage for concurrency cap and queue promotion
8. `9180a5a` ✨ feat(frontend): expose queueState in AnalysisJob snapshot type
9. `63a4807` ✨ feat(frontend): surface queue position and concurrency cap in LoadingState
10. `4e04089` 🧪 test(frontend): cover queueState propagation through useAnalysisJob
11. `5540912` 📝 docs(api): document queueState and updated 429 semantics
12. `5756cc2` 📝 docs(architecture): describe concurrency cap and queueState computation
13. `a0113d4` 📝 docs(sdd): tick frontend/docs tasks and append smoke checklist
14. `bd15831` 📝 docs(architecture): correct queueCapacity default to 256 in async submission note

## Rollback Reference

See `proposal.md` § "Rollback Plan" inside this archived folder. Key levers: revert the 14 commits listed above in reverse order, drop the V7 composite index (or revert the migration) and restore the previous `AnalysisConcurrencyGuard`-based path. The previous `Saturated executor rejects synchronously` semantics is preserved in the `2026-05-23-observable-analysis-jobs` archive for reference; reverting this delta restores it transparently.
