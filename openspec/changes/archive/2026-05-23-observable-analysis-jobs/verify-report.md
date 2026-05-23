# Verify Report — observable-analysis-jobs

**Date**: 2026-05-23
**Branch**: `fix/frontend-changes-app-jar`
**Verdict**: **PASS WITH DEFERRED**

---

## Summary

La capability nueva `analysis-jobs` está implementada end-to-end (dominio, persistencia con migración `V6`, capa application con executor + reaper + scheduler, capa web con `AnalysisJobController` y exclusión del rate limiter, frontend con `useAnalysisJob` + clamp 99 % + integración en `DashboardPage`). Las cuatro gates pasan, los 11 requisitos del spec y los 8 acceptance scenarios cross-cutting tienen archivo(s) de test correspondientes. Quedan dos tareas explícitamente diferidas (`5.9` y `8.5`), documentadas en `tasks.md` y razonadas; no bloquean el archive.

---

## Spec Requirements Coverage

| # | Requirement | Production evidence | Test evidence | Status |
|---|-------------|---------------------|---------------|--------|
| 1 | Asynchronous Submission Contract (`202 / 400 / 429`) | `AnalysisJobController`, `AnalysisJobSubmissionService`, `RepositoryIntakeExceptionHandler` | `AnalysisJobControllerTest`, `AnalysisJobSubmissionServiceTest` | OK |
| 2 | Job Status Domain (`QUEUED/RUNNING/SUCCESS/FAILED`, terminal inmutable) | `domain/job/AnalysisJobStatus.java`, `JpaAnalysisJobProgressEmitter.fail` no-op terminal | `AnalysisJobStatusTest`, `JpaAnalysisJobProgressEmitterTest` | OK |
| 3 | Job Phase Domain + transitions (forward-only, salto libre a FAILED) | `domain/job/AnalysisJobPhase.java` (`canTransitionTo`, `forwardRank`) | `AnalysisJobPhaseTest` | OK |
| 4 | Progress Invariant (`≤99` mientras no SUCCESS+analysisId) | `JpaAnalysisJobProgressEmitter` clamp, CHECK SQL `chk_analysis_job_progress_invariant` en `V6` | `JpaAnalysisJobProgressEmitterTest`, `JpaAnalysisJobRepositoryTest` (rechazo de `progress=100` sin `analysis_id`) | OK |
| 5 | Job Timestamps (`createdAt/updatedAt/startedAt/completedAt`) | `AnalysisJobEntity` (`@PrePersist`/`@PreUpdate`), `JpaAnalysisJobRepository` | `JpaAnalysisJobRepositoryTest`, `JpaAnalysisJobProgressEmitterTest` | OK |
| 6 | Failure Payload (`errorCode`/`errorMessage` no-nulos en FAILED) | `AnalysisJobErrorCode`, `JpaAnalysisJobProgressEmitter.fail`, CHECK SQL en `V6` | `AnalysisJobExecutionServiceTest`, `JpaAnalysisJobProgressEmitterTest` | OK |
| 7 | Optional Pipeline Metrics (no-negativas, no regresan a null) | `AnalysisJobMetrics` (canonical ctor valida no-negativos), emitter `updateMetrics` | `AnalysisJobMetricsTest`, `AnalysisJobExecutionServiceTest` | OK |
| 8 | Job Observability Endpoint (`GET /api/analyze/jobs/{id}`) — 200/404, sin rate-limit, terminal queryable | `AnalysisJobController`, `WebMvcConfiguration` (`excludePathPatterns("/api/analyze/jobs/**", "/api/analyze/*")`), `AnalysisJobQueryService` | `AnalysisJobControllerTest` (queued/success/failed/404/no-429), `AnalyzeRateLimitInterceptorTest` | OK |
| 9 | Persisted Job State Survives + Retention configurable (7d/30d) | `AnalysisJobRetentionScheduler` + `AnalyzeThrottleProperties.Retention` + cron config | `AnalysisJobRetentionSchedulerTest`, `AnalysisJobRetentionSchedulerIntegrationTest` | OK |
| 10 | Process Restart Reconciles Non-Terminal (`JOB_INTERRUPTED`) | `AnalysisJobReaper` (`ApplicationRunner`) | `AnalysisJobReaperTest`, `AnalysisJobReaperIntegrationTest` | OK |
| 11 | Pre-existing Endpoints Preserved | `RepositoryAnalysisController` (sólo GETs/badge/og-image/leaderboards), `WebMvcConfiguration` (excluye `/api/analyze/*` del rate limiter) | `RepositoryAnalysisControllerTest`, `PricingControllerTest`, `RepositoryIntakeControllerTest` | OK |
| 12 | Client Progress Invariants (no 100 % sin SUCCESS+analysisId, navega, surface error.message) | `frontend/src/utils/analysisJobProgress.ts` (clamp `Math.min(99, …)`), `frontend/src/hooks/useAnalysisJob.ts` (setTimeout + AbortController + stop on terminal), `frontend/src/pages/DashboardPage.tsx` | `analysisJobProgress.test.ts` (7), `useAnalysisJob.test.ts` (3) | OK |

> El spec lista 11 “Requirement” + 1 cross-cutting de cliente que sí tiene su propio bloque (`Client Progress Invariants`). Cobertura: 12/12 sin gaps.

---

## Cross-cutting Acceptance Scenarios

| # | Scenario | Coverage |
|---|----------|----------|
| 1 | Long analysis remains below 100 % until SUCCESS+analysisId | `analysisJobProgress.test.ts`, `useAnalysisJob.test.ts`, `JpaAnalysisJobProgressEmitterTest` (clamp en BD) |
| 2 | Large repo en `CALCULATING_COSTS`/`SAVING_REPORT` nunca reporta 100 | `JpaAnalysisJobProgressEmitterTest` (clamp), `AnalysisJobExecutionServiceTest` (orden de fases + sólo `success` pone 100) |
| 3 | Clone failure → persisted FAILED, queryable | `AnalysisJobExecutionServiceTest` (`CLONE_TIMEOUT`), `AnalysisJobControllerTest` (GET FAILED → 200) |
| 4 | Successful submission → SUCCESS con analysisId | `AnalysisJobExecutionServiceTest` (happy path), `AnalysisJobControllerTest` (GET SUCCESS) |
| 5 | Invalid URL no inserta job row | `AnalysisJobSubmissionServiceTest`, `AnalysisJobControllerTest` |
| 6 | Rate-limited submission no inserta job row | `AnalysisJobSubmissionServiceTest` (`TaskRejectedException` → repo.save + deleteById + 429) |
| 7 | Polling unknown jobId → 404 | `AnalysisJobControllerTest` |
| 8 | Crashed process restart → FAILED/JOB_INTERRUPTED | `AnalysisJobReaperTest`, `AnalysisJobReaperIntegrationTest` |

Cobertura: 8/8.

---

## Design Alignment

- **Hexagonal boundaries**: `domain/job` no depende de Spring/JPA (revisado: solo `record`/`enum`). Puerto `AnalysisJobRepository` vive en `application/analyzer`; adapter JPA en `infrastructure/persistence/analysis/jobs`. OK.
- **Async wiring**: `AnalysisJobExecutionService.runJob` lleva `@Async("analysisJobExecutor")` (línea 98). El bean `analysisJobExecutor` se crea en `AsyncExecutionConfig` con `ThreadPoolTaskExecutor` + `AbortPolicy` (rejection → `TaskRejectedException` → 429). OK.
- **Rate-limit exclusion**: `WebMvcConfiguration` aplica el `AnalyzeRateLimitInterceptor` sólo a `POST /api/analyze` y `POST /api/repositories/intake`, excluyendo `"/api/analyze/jobs/**"` y `"/api/analyze/*"` (lectura no rate-limited). OK.
- **DB invariants**: `V6__analysis_job.sql` incluye `chk_analysis_job_progress_invariant` (`progress<=99 OR (status='SUCCESS' AND analysis_id IS NOT NULL)`) y `ON DELETE RESTRICT` sobre `analysis(id)` para preservar la invariante `progress=100 ⇒ analysisId no nulo`. OK.
- **Frontend clamp**: helper puro `frontend/src/utils/analysisJobProgress.ts:45` aplica `Math.max(0, Math.min(99, raw))`. La excepción a 100 sólo se concede en el rama `status === 'SUCCESS' && analysisId !== null`. OK.
- **Polling cadence**: `useAnalysisJob.ts` usa `setTimeout` encadenado y `AbortController` por request, sin `setInterval` en el pipeline (rg confirma cero `setInterval` en `frontend/src`). OK.
- **Concurrency guard removal**: `AnalysisConcurrencyGuard` ya no existe en `application/analyzer/` (confirmado por `ls`). La cola del executor reemplaza el semáforo (paridad: rejection → 429). OK.
- **Migrations sealed**: `git log main..HEAD -- backend/src/main/resources/db/migration/V[1-5]*.sql` no devuelve commits en la rama. V1–V5 inmutables. OK.

---

## Gates

| Gate | Resultado |
|------|-----------|
| `./gradlew check` (backend) | PASS — 189 tests, 0 failures, 0 errors, 0 skipped; checkstyle + spotless OK |
| `npm run lint` (frontend) | PASS — exit 0 |
| `npm run build` (frontend) | PASS — `tsc -b && vite build` OK, bundle ~371 KB |
| `npm run test` (frontend) | PASS — 10/10 tests (`useAnalysisJob.test.ts` 3, `analysisJobProgress.test.ts` 7) |

---

## Commits in Scope

Rango: ancestro común inmediato `b2be0a2` … `HEAD` (`5790218`). 14 commits, todos en formato gitmoji + conventional:

```
5790218 docs(sdd): close observable-analysis-jobs tasks and add smoke checklist
689966d docs(architecture): describe analysis job executor and lifecycle
d1d82ce docs(api): document async analysis jobs endpoints and 202 contract
7bf39b3 docs(sdd): tick frontend tasks for observable-analysis-jobs
0d30606 test(frontend): cover useAnalysisJob and progress clamp invariants
1b44543 refactor(frontend): rewire LoadingState to backend job status
55cf388 feat(frontend): add useAnalysisJob polling hook and API client
1cd9a55 chore(frontend): add vitest, RTL and jsdom test setup
a4263be docs(sdd): tick application/executor/web tasks for observable-analysis-jobs
7368c14 test(jobs): integration coverage for executor, reaper and retention
db0bdbc test(jobs): cover analysis job application services
3d6fd00 feat(jobs): wire async analysis job pipeline end-to-end
34264ee db(jobs): add analysis_job persistence and V6 migration
2bf8770 feat(jobs): add AnalysisJob domain types and enums
```

---

## Deferred Items (no-blocker)

- **5.9 — `AnalysisJobControllerAsyncTest`**: medición de latencia del POST con executor bloqueado por `CountDownLatch`. La asincronía vive en `AnalysisJobExecutionService.runJob`, no en el controller; cobertura indirecta vía `AnalysisJobSubmissionServiceTest` + `AnalysisJobControllerTest`. Tracked en `tasks.md > Deferred follow-ups`.
- **8.5 — `frontend/README.md` con instrucciones de testing**: no existe README de frontend; documentación de `npm run test` vive en `CLAUDE.md` y en `frontend/package.json`. Diferido para evitar duplicación. Tracked en `tasks.md > Deferred follow-ups`.

Tareas completadas: 76/78 marcadas `[x]`, 2 marcadas `[~]` con justificación.

---

## Findings

**Critical**: ninguna.

**Warnings**: ninguna.

**Notes / Suggestions**:

1. La task `10.1` (smoke test manual) está marcada `[x]` con el plan documentado en `openspec/changes/observable-analysis-jobs/smoke.md`, pero la ejecución real del checklist sigue siendo responsabilidad del PR. No bloquea archive, sólo conviene confirmar en la descripción del PR.
2. `AnalysisJobControllerTest` cubre los 8 escenarios del spec contra el slice `@WebMvcTest`. El test de “no rate-limit en polling” se hace verificando que el endpoint no entra en el path del interceptor; está soportado adicionalmente por la config explícita en `WebMvcConfiguration.excludePathPatterns`. Suficiente para esta entrega.

---

## Conclusion

**PASS WITH DEFERRED**. La implementación cumple el spec completo, los gates están verdes, las invariantes críticas (`progress ≤ 99` salvo SUCCESS+analysisId, fases forward-only, terminal inmutable, FAILED con payload no-nulo, reaper al arranque, retención configurable) están aseguradas en BD, dominio, application y frontend. Las dos tareas diferidas están justificadas y rastreadas en `tasks.md`.

**Next recommended**: `sdd-archive`.
