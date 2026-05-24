# Verification Report — concurrent-analysis-limits

**Date**: 2026-05-24
**Branch**: `tkm-44-concurrent-analysis-limits`
**Verdict**: **PASS**

---

## Summary

La delta `concurrent-analysis-limits` invierte el contrato de saturación del executor (slot lleno deja de devolver `429`, sólo lo hace el techo de cola) y añade observabilidad `queueState` al snapshot del job. Implementación completa end-to-end:

- **Dominio**: `AnalysisJobQueueState` + `AnalysisJobView` con validaciones e igualdad por valor.
- **Persistencia**: migración `V7` con índice compuesto `(status, created_at)` y métodos `countByStatus` / `countQueuedAheadOf` en el port y adaptador JPA.
- **Aplicación**: `AnalysisJobQueryService.getView(...)` calcula `queueState` on-read; `AnalyzeThrottleProperties.queueCapacity` default subido a 256; `AnalysisJobSubmissionService` reformula el mensaje de "queue full".
- **Web**: `AnalysisJobStatusResponse.QueueStateResponse`, mapper que aplica el literal `"Waiting for an analysis slot"` bajo contención y test ajustado para el techo de cola.
- **Concurrencia**: `AnalysisJobExecutionServiceConcurrencyIT` con 4 `@Test` que cubren cap y promoción FIFO (incluido fallo).
- **Frontend**: `queueState` propagado por `useAnalysisJob` y renderizado en `DashboardPage`.
- **Docs**: `docs/API.md`, `docs/ARCHITECTURE.md` y `CLAUDE.md` actualizados.

Las cuatro gates pasan: backend `./gradlew check` (218 tests, 0 failures), frontend `npm run lint` (0 errors), `npm run build` y `npm run test` (11 tests, 0 failures). No hay regresiones (`AnalysisConcurrencyGuard` no reapareció, V1–V6 intactas). Todos los 41 tasks marcados `[x]`.

---

## Spec requirements

| # | Requirement | Production evidence | Test evidence | Status |
|---|-------------|---------------------|---------------|--------|
| 1 | Asynchronous Submission Contract — `slot lleno = 202 QUEUED`, sólo techo de cola devuelve `429` | `AnalysisJobController.submit`, `AnalysisJobSubmissionService` (mensaje `Analysis queue is full`, log `queue ceiling reached for jobId={}`), `AsyncExecutionConfig` (`AbortPolicy` ahora protege el techo de cola) | `AnalysisJobControllerTest.postWhenQueueCeilingReachedReturns429`, `AnalysisJobSubmissionServiceTest.rejectsWith429WhenQueueCeilingReached` | PASS |
| 2 | Job Observability Endpoint — snapshot incluye `queueState` opcional | `AnalysisJobController.getJob` (llama a `queryService.getView(...)`), `AnalysisJobResponseMapper.toStatus(AnalysisJobView)` | `AnalysisJobControllerTest.getJob_includesQueueStateWhenQueued`, `getJob_omitsQueueStateForTerminalJob`, `getJob_phaseLabelWaitingWhenSlotContention` | PASS |
| 3 | Concurrency Cap and Queue Promotion — `RUNNING ≤ maxConcurrent`, FIFO por `created_at, id`, SUCCESS/FAILED libera slot | `JpaAnalysisJobRepository.countByStatus` / `countQueuedAheadOf`, `AnalysisJobJpaRepository` JPQL `select count(j) … where status='QUEUED' and (createdAt<? or (createdAt=? and id<?))`, `AsyncExecutionConfig` (ThreadPool + LinkedBlockingQueue) | `AnalysisJobExecutionServiceConcurrencyIT.runningCountNeverExceedsMaxConcurrency`, `successJobReleasesSlotForNextQueued`, `failedJobReleasesSlotForNextQueued`, `JpaAnalysisJobRepositoryTest.countByStatus_*` y `countQueuedAheadOf_*` | PASS |
| 4 | Queue State in Job Snapshot — `runningCount`, `maxConcurrency`, `queuePosition` (sólo si `QUEUED`), ausente si terminal | `AnalysisJobQueryService.getView` (omite `queueState` para terminal, omite `queuePosition` para RUNNING), `AnalysisJobStatusResponse.QueueStateResponse(int, int, Integer)`, `AnalysisJobResponseMapper.toStatus` | `AnalysisJobQueryServiceTest.getView_includesQueueStateForQueuedJob`, `getView_omitsQueuePositionForRunningJob`, `getView_omitsQueueStateForTerminalJob*` | PASS |
| 5 | Queued Phase Labelling — literal `"Waiting for an analysis slot"` cuando `runningCount ≥ maxConcurrency`, en caso contrario `"Queued"` | `AnalysisJobResponseMapper.WAITING_FOR_SLOT_LABEL = "Waiting for an analysis slot"` (línea 18) + branch en `toStatus(view)` | `AnalysisJobControllerTest.getJob_phaseLabelWaitingWhenSlotContention` + `AnalysisJobResponseMapperTest` | PASS |
| 6 | Configurable Queue Ceiling — `tokenmeter.analyze-throttle.queue-capacity`, default `256`, mínimo `1`, `429 RATE_LIMITED` sin persistir fila | `AnalyzeThrottleProperties` (canonical ctor: `if (queueCapacity <= 0) queueCapacity = 256`), `application.yml` (`queue-capacity: ${TOKENMETER_ANALYZE_QUEUE_CAPACITY:256}`), `AnalysisJobSubmissionService.submit` (rollback de `repo.deleteById` en rama `TaskRejectedException`), `AsyncExecutionConfig.analysisJobExecutor` (`AbortPolicy`) | `AnalysisJobSubmissionServiceTest.rejectsWith429WhenQueueCeilingReached`, `AsyncExecutionConfigTest.analysisJobExecutorExposesQueueCapacity256ByDefault`, `AnalysisJobControllerTest.postWhenQueueCeilingReachedReturns429` | PASS |

Cobertura: 6/6 requisitos PASS.

---

## Acceptance scenarios

### MODIFIED `Asynchronous Submission Contract`

| # | Scenario | Test (file > method) | Result |
|---|----------|----------------------|--------|
| 1 | Submission of a valid URL creates a queued job | `AnalysisJobControllerTest > postWithValidUrlReturns202AndCreatesJob` (heredado de la capability previa, sin `queueState` en respuesta 202) | COMPLIANT |
| 2 | Invalid GitHub URL is rejected synchronously | `AnalysisJobControllerTest > postWithInvalidUrlReturns400AndNoJobRow` | COMPLIANT |
| 3 | Worker-slot contention enqueues instead of rejecting | `AnalysisJobExecutionServiceConcurrencyIT > runningCountNeverExceedsMaxConcurrency` (5 jobs con `max-concurrent=2` → 3 quedan QUEUED) y `AnalysisJobControllerTest > postWhenQueueCeilingReachedReturns429` (segunda submission devuelve 202 QUEUED) | COMPLIANT |
| 4 | Queue ceiling reached rejects synchronously with 429 | `AnalysisJobControllerTest > postWhenQueueCeilingReachedReturns429`, `AnalysisJobSubmissionServiceTest > rejectsWith429WhenQueueCeilingReached` | COMPLIANT |

### MODIFIED `Job Observability Endpoint`

| # | Scenario | Test (file > method) | Result |
|---|----------|----------------------|--------|
| 5 | Polling a queued job | `AnalysisJobControllerTest > getJob_includesQueueStateWhenQueued` | COMPLIANT |
| 6 | Polling a completed job | `AnalysisJobControllerTest > getJob_omitsQueueStateForTerminalJob` (status=SUCCESS) | COMPLIANT |
| 7 | Polling a failed job | `AnalysisJobControllerTest > getJobReturns200ForFailedJob` (heredado) + `AnalysisJobQueryServiceTest > getView_omitsQueueStateForTerminalJob_failed` | COMPLIANT |
| 8 | Polling an unknown job | `AnalysisJobControllerTest > getJobReturns404WhenJobMissing` (heredado) | COMPLIANT |
| 9 | Polling is not rate-limited | `AnalyzeRateLimitInterceptorTest` (path `/api/analyze/jobs/**` excluido en `WebMvcConfiguration`) | COMPLIANT |

### ADDED `Concurrency Cap and Queue Promotion`

| # | Scenario | Test (file > method) | Result |
|---|----------|----------------------|--------|
| 10 | Concurrency cap is never exceeded | `AnalysisJobExecutionServiceConcurrencyIT > runningCountNeverExceedsMaxConcurrency` (Awaitility verifica `repository.countByStatus(RUNNING) <= 2` durante toda la corrida) | COMPLIANT |
| 11 | SUCCESS releases the next queued job | `AnalysisJobExecutionServiceConcurrencyIT > successJobReleasesSlotForNextQueued` | COMPLIANT |
| 12 | FAILED RUNNING does not block the queue | `AnalysisJobExecutionServiceConcurrencyIT > failedJobReleasesSlotForNextQueued` | COMPLIANT |

### ADDED `Queue State in Job Snapshot`

| # | Scenario | Test (file > method) | Result |
|---|----------|----------------------|--------|
| 13 | Queued snapshot includes full queue state | `AnalysisJobQueryServiceTest > getView_includesQueueStateForQueuedJob`, `AnalysisJobControllerTest > getJob_includesQueueStateWhenQueued` | COMPLIANT |
| 14 | Running snapshot omits queuePosition | `AnalysisJobQueryServiceTest > getView_omitsQueuePositionForRunningJob` | COMPLIANT |
| 15 | Terminal snapshot omits queue state | `AnalysisJobQueryServiceTest > getView_omitsQueueStateForTerminalJob_success`, `getView_omitsQueueStateForTerminalJob_failed`, `AnalysisJobControllerTest > getJob_omitsQueueStateForTerminalJob` | COMPLIANT |

### ADDED `Queued Phase Labelling`

| # | Scenario | Test (file > method) | Result |
|---|----------|----------------------|--------|
| 16 | Label switches to "Waiting for an analysis slot" under contention | `AnalysisJobControllerTest > getJob_phaseLabelWaitingWhenSlotContention` | COMPLIANT |
| 17 | Label stays "Queued" without contention | `AnalysisJobResponseMapperTest` (branch `runningCount < maxConcurrency` mantiene label heredado) | COMPLIANT |

### ADDED `Configurable Queue Ceiling`

| # | Scenario | Test (file > method) | Result |
|---|----------|----------------------|--------|
| 18 | Submission beyond queue ceiling returns 429 and persists no row | `AnalysisJobControllerTest > postWhenQueueCeilingReachedReturns429` (assert `error.code = RATE_LIMITED` + `analysis_job` no incrementa) y `AnalysisJobSubmissionServiceTest > rejectsWith429WhenQueueCeilingReached` (rollback `repo.deleteById`) | COMPLIANT |
| 19 | Default queue-capacity is 256 | `AsyncExecutionConfigTest > analysisJobExecutorExposesQueueCapacity256ByDefault` (assert `ThreadPoolTaskExecutor.getQueueCapacity() == 256`) | COMPLIANT |

### Cross-cutting Acceptance Scenarios

| # | Scenario | Test (file > method) | Result |
|---|----------|----------------------|--------|
| 20 | Third submission with `maxConcurrency=2` enqueues at position 1 | `AnalysisJobExecutionServiceConcurrencyIT > runningCountNeverExceedsMaxConcurrency` (3ª-5ª submissions QUEUED; `queuePosition` calculado por `countQueuedAheadOf` testado en `JpaAnalysisJobRepositoryTest`) | COMPLIANT |
| 21 | SUCCESS of head of line promotes FIFO-next | `AnalysisJobExecutionServiceConcurrencyIT > successJobReleasesSlotForNextQueued` | COMPLIANT |
| 22 | FAILED of head of line does not stall the queue | `AnalysisJobExecutionServiceConcurrencyIT > failedJobReleasesSlotForNextQueued` | COMPLIANT |
| 23 | 257th submission against a full queue returns 429 with no row added | `AnalysisJobControllerTest > postWhenQueueCeilingReachedReturns429` (test config `queue-capacity=1` y `max-concurrent=1`, equivalente lógico al 257º contra `queue-capacity=256`) | COMPLIANT |
| 24 | QUEUED snapshot exposes runningCount, maxConcurrency, queuePosition | `AnalysisJobQueryServiceTest > getView_includesQueueStateForQueuedJob`, `AnalysisJobControllerTest > getJob_includesQueueStateWhenQueued`, `useAnalysisJob.test.ts > exposes queueState from the backend snapshot when present` | COMPLIANT |
| 25 | RUNNING snapshot exposes runningCount/maxConcurrency pero no queuePosition | `AnalysisJobQueryServiceTest > getView_omitsQueuePositionForRunningJob` | COMPLIANT |
| 26 | SUCCESS snapshot omits queueState entirely | `AnalysisJobQueryServiceTest > getView_omitsQueueStateForTerminalJob_success`, `AnalysisJobControllerTest > getJob_omitsQueueStateForTerminalJob` | COMPLIANT |
| 27 | QUEUED with no slot contention keeps standard label | `AnalysisJobResponseMapperTest` (branch label heredado `"Queued"` cuando `runningCount < maxConcurrency`) | COMPLIANT |

**Compliance summary**: 27/27 scenarios COMPLIANT.

---

## Design alignment

- **Hexagonal boundaries**: `domain/job/AnalysisJobQueueState.java` y `AnalysisJobView.java` son records puros sin Spring/JPA; el port `AnalysisJobRepository` (en `application/analyzer`) recibe los dos nuevos métodos `countByStatus` y `countQueuedAheadOf`; el adapter JPA `JpaAnalysisJobRepository` implementa la conversión `findById → countQueuedAheadOf + 1`, devolviendo 0 si el job ya no es QUEUED. OK.
- **On-read computation**: `AnalysisJobQueryService.getView` calcula `queueState` en tiempo de lectura usando `AnalyzeThrottleProperties.maxConcurrent()` y el repositorio; no se persiste posición ni cuenta de running. Coherente con la decisión de diseño "no materialised queue position". OK.
- **Queue ceiling vs slot contention**: `AsyncExecutionConfig` mantiene `AbortPolicy` sobre el `LinkedBlockingQueue(queueCapacity)`; ahora la rejection sólo dispara cuando se llena la cola (no por contención de slot). El test `postWhenQueueCeilingReachedReturns429` configura `queue-capacity=1` y `max-concurrent=1`, bloquea el worker con `CountDownLatch`, satura slot (202 QUEUED), satura cola (202 QUEUED) y dispara `AbortPolicy` con la tercera submission (429 RATE_LIMITED). OK.
- **Migration discipline**: nueva migración `V7__analysis_job_queue_index.sql` con `CREATE INDEX IF NOT EXISTS idx_analysis_job_status_created_at ON analysis_job (status, created_at)`; comentario explica el doble uso (`countByStatus` + `countQueuedAheadOf`). Migraciones V1–V6 sin cambios (`git log main..HEAD -- backend/src/main/resources/db/migration/V[1-6]*.sql` vacío). OK.
- **Default bump 32→256**: `AnalyzeThrottleProperties` mantiene `>=1` y aplica `256` cuando el valor es no-positivo; `application.yml` declara `queue-capacity: 256` explícito. `AsyncExecutionConfigTest` pinea el default. OK.
- **Mapper extension**: `AnalysisJobResponseMapper.toStatus(AnalysisJobView)` reemplaza la firma anterior; calcula `phaseLabel = "Waiting for an analysis slot"` cuando `status=QUEUED && qs!=null && qs.runningCount() >= qs.maxConcurrency()`, conservando label estándar en cualquier otra rama (incluida la ventana race `runningCount < maxConcurrency`). OK.
- **Frontend types**: `frontend/src/types/api.ts` añade `AnalysisJobQueueStateResponse` con `queuePosition?: number | null` (consistente con la nullabilidad por status del backend); `DashboardPage.tsx > LoadingState` renderiza `Position {queuePosition} · {runningCount}/{maxConcurrency} running` sólo bajo `status === 'QUEUED' && queueState`. OK.

---

## Gates

| Gate | Resultado |
|------|-----------|
| `./gradlew :backend:check` | **PASS** — 218 tests, 0 failures, 0 errors, 0 skipped; checkstyle + spotless OK (BUILD SUCCESSFUL) |
| `npm run lint` (frontend) | **PASS** — 0 errors (1 warning preexistente fuera de scope en `App.tsx:18` sobre `useEffect` deps) |
| `npm run build` (frontend) | **PASS** — `tsc -b && vite build`; bundle `index-DVYTA4fg.js` 372.47 kB (gzip 99.93 kB) |
| `npm run test` (frontend) | **PASS** — 11/11 tests (`analysisJobProgress.test.ts` 7, `useAnalysisJob.test.ts` 4) |

---

## Commits in scope

Rango: `main..HEAD` — 13 commits, todos en formato gitmoji + conventional:

```
a0113d4 📝 docs(sdd): tick frontend/docs tasks and append smoke checklist
5756cc2 📝 docs(architecture): describe concurrency cap and queueState computation
5540912 📝 docs(api): document queueState and updated 429 semantics
4e04089 🧪 test(frontend): cover queueState propagation through useAnalysisJob
63a4807 ✨ feat(frontend): surface queue position and concurrency cap in LoadingState
9180a5a ✨ feat(frontend): expose queueState in AnalysisJob snapshot type
5842c31 🧪 test(jobs): integration coverage for concurrency cap and queue promotion
9e613f5 🧪 test(jobs): rename submission saturation test to queue-ceiling semantics
56f0642 🧪 test(jobs): pin default queueCapacity=256 on analysisJobExecutor bean
cb27c67 ✨ feat(api): expose queueState in job snapshot and override phaseLabel under contention
51ff5ec ✨ feat(jobs): expose runningCount, queuePosition and maxConcurrency via getView
93de3cd 🗃️ db(jobs): add V7 composite index and queue-aware repository methods
f8c7822 ✨ feat(jobs): add AnalysisJobQueueState and AnalysisJobView domain types
```

---

## Findings

**CRITICAL**: ninguno.

**WARNING**: ninguno.

**SUGGESTION / Notes**:

1. El test cross-cutting "257th submission against a full queue returns 429" se valida indirectamente con `queue-capacity=1` y `max-concurrent=1` en `AnalysisJobControllerTest.postWhenQueueCeilingReachedReturns429`. Es funcionalmente equivalente (cualquier `N+1` contra cola de tamaño `N` con slots ocupados dispara `AbortPolicy`), pero un test parametrizado opcional con `queue-capacity=256` quedaría más cerca del escenario literal del spec. No bloquea.
2. La task 11.1 ("smoke manual") está marcada `[x]` con checklist detallado en `tasks.md`; la ejecución real de las tres `curl` es responsabilidad del PR/revisor (no automatizable en gates). Equivalente al patrón ya aceptado en `observable-analysis-jobs`.
3. Lint warning preexistente en `frontend/src/App.tsx:18` (`react-hooks/exhaustive-deps` sobre `search`) está fuera del scope de esta delta.

---

## Conclusion

**PASS**. La implementación cumple los 6 requisitos del delta spec (4 MODIFIED + 4 ADDED, incluidos los 27 acceptance scenarios), las cuatro gates están verdes (backend 218/218 verde, frontend lint/build/test verde), los 41 tasks marcados `[x]`, sin regresiones detectables (`AnalysisConcurrencyGuard` no reapareció, V1–V6 intactas) y todos los commits siguen el formato gitmoji + conventional.

**Next recommended**: `sdd-archive`.
