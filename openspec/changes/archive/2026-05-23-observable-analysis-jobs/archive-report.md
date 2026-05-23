# Archive Report: observable-analysis-jobs

Archived: 2026-05-23

## Outcome

- Verify status: `pass_with_deferred` (commit `e4e9b57`, `verify-report.md`).
- All happy-path scenarios in the promoted `analysis-jobs` spec are covered by the executor, integration, frontend hook and persistence tests landed in this change.
- Two non-blocking items were deferred and tracked below.
- Recommended by `sdd-verify` with disposition `ARCHIVE`.

## Merged Specs

| Delta path | Main spec path |
|---|---|
| `openspec/changes/observable-analysis-jobs/specs/analysis-jobs/spec.md` | `openspec/specs/analysis-jobs/spec.md` |

Initial population of the `analysis-jobs` capability — no existing scenarios overwritten. No destructive deltas (no removals or Flyway baseline changes) per `openspec/config.yaml` rules.archive.

## Summary

- New `analysis-jobs` capability: `POST /api/analyze` becomes an asynchronous submission (HTTP `202` + `jobId`), `GET /api/analyze/jobs/{jobId}` polls the snapshot.
- Domain types and lifecycle: `AnalysisJobStatus`, `AnalysisJobPhase`, status/phase transitions and the `progressPercent ≤ 99` invariant pinned by spec scenarios.
- Persistence + migration V6 (`analysis_job` table) + boot-time reaper that reconciles non-terminal rows to `FAILED/JOB_INTERRUPTED`, plus a retention scheduler with configurable success (7 d) and failure (30 d) windows.
- Execution pipeline runs in a `ThreadPoolTaskExecutor` (`analysisJobExecutor`) with `REQUIRES_NEW` emitter publishing phase, progress and metrics; rate-limited saturation surfaces as synchronous `429`.
- Frontend: new `useAnalysisJob` polling hook (1.5 s cadence), `LoadingState` rewired to backend phase/progress, Vitest + RTL harness for the hook and progress-clamp invariants; UI never reports 100 % until `SUCCESS` with `analysisId`.
- Documentation: `docs/ARCHITECTURE.md` (executor + lifecycle) and `docs/API.md` (202 contract, job snapshot, `JOB_INTERRUPTED`) updated.

## Deferred Items

Tracked in `verify-report.md`; not blocking archival:

- Task 5.9 — `AnalysisJobControllerAsyncTest` (`@WebMvcTest` async slice covering the `202` contract end-to-end at the MVC layer). Existing service and integration coverage already exercises the same path.
- Task 8.5 — Frontend `README` update covering the new Vitest setup and `useAnalysisJob` testing approach.

## Commits

Gitmoji + Conventional Commits introduced by this change (oldest first, range `2bf8770..e4e9b57`):

1. `2bf8770` ✨ feat(jobs): add AnalysisJob domain types and enums
2. `34264ee` 🗃️ db(jobs): add analysis_job persistence and V6 migration
3. `3d6fd00` ✨ feat(jobs): wire async analysis job pipeline end-to-end
4. `db0bdbc` 🧪 test(jobs): cover analysis job application services
5. `7368c14` 🧪 test(jobs): integration coverage for executor, reaper and retention
6. `a4263be` 📝 docs(sdd): tick application/executor/web tasks for observable-analysis-jobs
7. `1cd9a55` 🔧 chore(frontend): add vitest, RTL and jsdom test setup
8. `55cf388` ✨ feat(frontend): add useAnalysisJob polling hook and API client
9. `1b44543` ♻️ refactor(frontend): rewire LoadingState to backend job status
10. `0d30606` 🧪 test(frontend): cover useAnalysisJob and progress clamp invariants
11. `7bf39b3` 📝 docs(sdd): tick frontend tasks for observable-analysis-jobs
12. `d1d82ce` 📝 docs(api): document async analysis jobs endpoints and 202 contract
13. `689966d` 📝 docs(architecture): describe analysis job executor and lifecycle
14. `5790218` 📝 docs(sdd): close observable-analysis-jobs tasks and add smoke checklist
15. `e4e9b57` 📝 docs(sdd): add verify-report for observable-analysis-jobs

## Rollback Reference

See `proposal.md` § "Rollback Plan" inside this archived folder. Key levers: revert the 15 commits listed above in reverse order, drop the `analysis_job` table (or revert the V6 migration) and restore the previous synchronous `POST /api/analyze` controller behaviour. The asynchronous contract is additive over `GET /api/analyze/{id}`, which was preserved unchanged.
