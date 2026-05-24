# Proposal: Concurrent Analysis Limits with Visible Queueing (TKM-44)

## Intent

TKM-43 dejó un límite de concurrencia cableado pero invisible: cuando los N workers están ocupados, `POST /api/analyze` devuelve `429 RATE_LIMITED` y descarta la fila. TKM-44 convierte ese límite en una **cola FIFO observable**: la saturación de slots produce `202 Accepted` con `status=QUEUED`, los jobs se promueven automáticamente a `RUNNING` al liberarse un worker, y el cliente ve su posición en cola vía `GET /api/analyze/jobs/{jobId}`. Solo se reserva `429` para el techo de cola real (capacidad finita = protección anti-DoS).

## Scope

### In Scope
- Backend: `AnalysisJobSubmissionService` deja de tratar `RejectedExecutionException` como contención de slot; sólo el overflow del queue interno (`queueCapacity=256`) dispara 429.
- Backend: `AnalysisJobQueryService` extendido para calcular e inyectar `queueState { runningCount, maxConcurrency, queuePosition? }` (queuePosition sólo cuando `status=QUEUED`).
- Backend: nuevo DTO field `queueState` en `AnalysisJobStatusResponse`; `AnalysisJobResponseMapper` lo produce.
- Backend: `phaseLabel` = `"Waiting for an analysis slot"` cuando `status=QUEUED AND runningCount >= maxConcurrency`; sino mantiene label actual.
- Backend: nueva propiedad `tokenmeter.analyze-throttle.queue-capacity` (default `256`, exposed as-is).
- Persistencia: Flyway `V7__analysis_job_queue_index.sql` añade índice compuesto `(status, created_at)` sobre `analysis_job` para acelerar `queuePosition` y `runningCount`.
- Frontend: `LoadingState` muestra subtítulo "Waiting for an analysis slot" + posición cuando `queuePosition != null`; tipos `AnalysisJobStatusResponse` actualizados.
- Docs: `docs/API.md` (nuevos campos + 429 redefinido), `docs/ARCHITECTURE.md` (sección concurrency cap), `CLAUDE.md` (línea de actualización).
- Tests: cap de concurrencia, promoción al liberar slot, aislamiento ante fallo del job en cabeza, techo de cola → 429.

### Out of Scope
- Dispatcher custom con polling de BD / `SKIP LOCKED` (Opción B descartada).
- `maxConcurrency` dinámico / live reload.
- Watchdog mid-run de `RUNNING` zombies (boot reaper + clone-timeout ya cubren).
- Idempotency-key en submission.
- `queueState` en respuesta `POST 202` (sólo en GET de status).
- Endpoint separado de métricas de cola.

## Approach

**Opción A** (recomendada en exploration): apoyarse en el FIFO interno del `ThreadPoolTaskExecutor`. Como `corePoolSize = maxPoolSize`, el `LinkedBlockingQueue` interno ya hace de cola de promoción automática. Sólo invertimos la rama de saturación: el `AbortPolicy` actual deja de dispararse por contención de slot (queue absorbe) y pasa a ser red de seguridad para el techo (`queue-capacity=256`). `queuePosition` y `runningCount` se computan **on read** desde la BD (sin estado adicional). El nuevo índice V7 mantiene el coste por polling acotado.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `application/analyzer/AnalysisJobSubmissionService.java` | Modified | Mensaje/comentario `"Server is busy"` → `"queue ceiling reached"`. Estructura intacta. |
| `application/analyzer/AnalysisJobQueryService.java` | Modified | Devuelve `AnalysisJobView` (snapshot + queueState). |
| `application/analyzer/AnalyzeThrottleProperties.java` | Modified | Default `queueCapacity=256`. |
| `application/analyzer/AnalysisJobRepository.java` | Modified | Nuevos métodos `countByStatus`, `queuePositionOf`. |
| `domain/job/AnalysisJobQueueState.java` | New | Record `(int runningCount, int maxConcurrency, Integer queuePosition)`. |
| `infrastructure/web/analyzer/AnalysisJobStatusResponse.java` | Modified | Añade `queueState` (nullable). |
| `infrastructure/web/analyzer/AnalysisJobResponseMapper.java` | Modified | Mapea queueState + computa phaseLabel "Waiting for an analysis slot". |
| `infrastructure/web/analyzer/AnalysisJobController.java` | Modified | Usa view en lugar de snapshot puro. |
| `infrastructure/persistence/analysis/jobs/AnalysisJobJpaRepository.java` | Modified | `countByStatus`, `countByStatusAndCreatedAtLessThan`. |
| `infrastructure/persistence/analysis/jobs/JpaAnalysisJobRepository.java` | Modified | Implementa nuevos métodos del port. |
| `backend/src/main/resources/db/migration/V7__analysis_job_queue_index.sql` | New | Índice compuesto `(status, created_at)`. |
| `backend/src/main/resources/application*.yml` | Modified | `analyze-throttle.queue-capacity: 256`. |
| `frontend/src/types/api.ts` | Modified | `queueState` añadido a `AnalysisJobStatusResponse`. |
| `frontend/src/pages/DashboardPage.tsx` (`LoadingState`) | Modified | Subtítulo + posición cuando `queueState.queuePosition`. |
| `openspec/specs/analysis-jobs/spec.md` | Delta | Invertir escenario *Saturated executor rejects synchronously*; añadir requirement de Concurrency Cap and Queue Promotion. |
| `docs/API.md`, `docs/ARCHITECTURE.md`, `CLAUDE.md` | Modified | Documentar nuevo contrato. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Cambio observable de contrato (saturación: 429 → 202+QUEUED) | High | Documentar en `docs/API.md` y changelog; recordar que el 429 sigue existiendo para techo de cola y rate-limit por IP. |
| `queuePosition` puede fluctuar entre polls | Medium | Etiquetar como estimación en UX; documentar monotonía típica (sólo decrece salvo fallos). |
| Crecimiento sin techo de filas `QUEUED` | Medium | `queue-capacity=256` + `AbortPolicy` residual mantienen techo finito. |
| Coste de `count(*)` por polling con cola larga | Low | Índice compuesto `(status, created_at)` en V7. |
| Migración de tests TKM-43 (`rejectsWith429...`, `postWhenExecutorSaturatedReturns429RateLimited`) | Medium | Invertir su significado a "se acepta como QUEUED"; mantener test residual para techo de cola. |
| Race entre `executor.execute` y `markStarted` | Low | Ya existe en TKM-43; no se introduce nada nuevo. |

## Rollback Plan

1. Revertir commits de la rama `tkm-44-concurrent-analysis-limits`.
2. Drop del índice V7: `DROP INDEX IF EXISTS idx_analysis_job_status_created_at;` (idempotente).
3. Bajar `queue-capacity` de vuelta a `32` vía config si fuese necesario.
4. Sin pérdida de datos: no se altera schema de columnas, sólo se añade un índice y campos opcionales en JSON.

## Dependencies

- TKM-43 (`observable-analysis-jobs`) ya en main: aporta polling endpoint, snapshot DTO y `AnalyzeThrottleProperties`.
- Sin dependencias externas nuevas.

## Success Criteria

- [ ] `GET /api/analyze/jobs/{jobId}` devuelve `queueState.runningCount`, `queueState.maxConcurrency` siempre que el job no sea terminal, y `queueState.queuePosition` únicamente cuando `status=QUEUED`.
- [ ] Con `maxConcurrent=2` y 4 submissions consecutivas: 2 quedan `RUNNING`, 2 `QUEUED`; al completar (success o fail) un `RUNNING`, uno de los `QUEUED` transiciona a `RUNNING` automáticamente.
- [ ] Cuando el queue interno alcanza 256 elementos, la submission número 257 devuelve `429 RATE_LIMITED` y no queda fila en `analysis_job`.
- [ ] El fallo de un job en cabeza no bloquea la cola (el worker queda libre independientemente del resultado).
- [ ] `phaseLabel="Waiting for an analysis slot"` aparece cuando `status=QUEUED AND runningCount >= maxConcurrency`.
- [ ] Frontend `LoadingState` muestra el subtítulo + posición sin rediseño estructural.
- [ ] `./gradlew check` y `npm run lint && npm run build` pasan.
- [ ] `docs/API.md`, `docs/ARCHITECTURE.md` y `CLAUDE.md` reflejan el nuevo contrato.
