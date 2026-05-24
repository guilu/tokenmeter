# Design: Concurrent Analysis Limits with Visible Queueing (TKM-44)

## Technical Approach

Convertimos el límite de concurrencia (ya cableado en TKM-43) en una **cola FIFO observable** apoyándonos en la cola interna del `ThreadPoolTaskExecutor`. No hay dispatcher nuevo, ni thread extra, ni columna nueva. La saturación de slots deja de disparar `429`; sólo el techo finito de cola (`queueCapacity=256`) sigue produciendo `429 RATE_LIMITED` como red anti-DoS. `runningCount`, `maxConcurrency` y `queuePosition` se computan **on read** desde `analysis_job` cuando el cliente sondea el estado, y se inyectan en la respuesta como `queueState`. El `phaseLabel` se deriva en el mapper para mostrar `"Waiting for an analysis slot"` cuando hay contención real. Mapea directamente al *Requirement: Concurrency Cap and Queue Promotion* y al delta sobre *Job Observability Endpoint* de la spec.

## Architecture Decisions

### Decision: `AnalysisJobView` wrapper vs extender `AnalysisJobSnapshot`

**Choice**: Crear un wrapper `AnalysisJobView(AnalysisJobSnapshot snapshot, AnalysisJobQueueState queueState)`. `queueState` puede ser `null`.
**Alternatives considered**: Añadir 3 campos opcionales a `AnalysisJobSnapshot`.
**Rationale**: El snapshot representa estado **persistido** (vive en la fila `analysis_job`); `queueState` se calcula on-read y es inherentemente volátil entre polls. Mezclarlos en un mismo record convertiría a `AnalysisJobSnapshot` en un DTO híbrido y obligaría a usar `null` con dos semánticas distintas ("no aplica" vs "no calculado"). El wrapper preserva la separación.

### Decision: Cola del executor vs dispatcher con `SKIP LOCKED`

**Choice**: Cola interna del `ThreadPoolTaskExecutor` (Opción A en exploration).
**Alternatives considered**: `AnalysisJobDispatcher` con `@Scheduled` + `SELECT ... FOR UPDATE SKIP LOCKED`.
**Rationale**: FIFO ya garantizado por `LinkedBlockingQueue` cuando `corePoolSize == maxPoolSize`. Promoción al liberar worker es nativa de `ThreadPoolExecutor`. TKM-44 es single-pod; `SKIP LOCKED` sería sobreingeniería con su propio test surface y +500 ms de latencia de promoción.

### Decision: `queuePosition` computado on-read vs columna persistida

**Choice**: On-read vía `count(*) WHERE status='QUEUED' AND ((created_at, id) < (target.created_at, target.id))` + 1.
**Alternatives considered**: Columna `queue_position` mantenida con triggers o batch update.
**Rationale**: La posición sólo tiene sentido en el instante del poll. Persistirla introduce drift, requiere recalcular en cada admisión/promoción y se desincronizaría con cualquier proceso que tocara filas fuera de banda (reaper, retention). Coste mitigado con el índice V7.

### Decision: Bump `queueCapacity` default a 256

**Choice**: Subir `queueCapacity` default de `32` a `256` en `AnalyzeThrottleProperties`.
**Alternatives considered**: Mantener `32`; introducir alias `maxQueue` y deprecar `queueCapacity`.
**Rationale**: Con el contrato anterior, `32` saturaba pronto pero sólo devolvía `429`. Con la nueva semántica, ese mismo `32` se convierte en techo duro de filas QUEUED visibles — demasiado bajo para una ráfaga normal. `256` da margen sin perder protección (a 2 KB/fila JSON son ~512 KB en BD como máximo). Mantener el nombre `queueCapacity` evita una migración de config en `application*.yml`.

## Data Flow

```
                 POST /api/analyze
                         │
                         ▼
        AnalysisJobSubmissionService
        ┌──────────────────────────────┐
        │ 1. parse URL                 │
        │ 2. INSERT analysis_job QUEUED│
        │ 3. executor.execute(runJob)  │
        └──────────────────────────────┘
                         │
                         ▼
            ThreadPoolTaskExecutor
        ┌──────────────────────────────┐
        │  workers[N=maxConcurrent]    │
        │  queue[<=queueCapacity=256]  │ ──► AbortPolicy ──► 429 (techo)
        └──────────────────────────────┘
                         │ slot libre
                         ▼
        AnalysisJobExecutionService.runJob
              (emitter.transition → status=RUNNING)


    GET /api/analyze/jobs/{jobId}
                         │
                         ▼
        AnalysisJobQueryService.getView(id)
        ┌──────────────────────────────┐
        │ snapshot = findById          │
        │ if !terminal:                │
        │   running = countByStatus    │
        │   max     = props.maxConc.   │
        │   pos     = countQueuedAhead │ (only if QUEUED)
        └──────────────────────────────┘
                         │
                         ▼
        AnalysisJobResponseMapper.toStatus(view)
              → queueState + phaseLabel
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `backend/.../domain/job/AnalysisJobQueueState.java` | Create | Record `(int runningCount, int maxConcurrency, Integer queuePosition)`. `queuePosition` nullable, 1-based. |
| `backend/.../domain/job/AnalysisJobView.java` | Create | Record `(AnalysisJobSnapshot snapshot, AnalysisJobQueueState queueState)`. `queueState` nullable cuando snapshot es terminal. |
| `backend/.../application/analyzer/AnalysisJobRepository.java` | Modify | Añade `int countByStatus(AnalysisJobStatus)` y `int countQueuedAheadOf(AnalysisJobId)`. |
| `backend/.../application/analyzer/AnalysisJobQueryService.java` | Modify | Inyecta `AnalyzeThrottleProperties`. Añade `Optional<AnalysisJobView> getView(AnalysisJobId)`. Mantiene `findById` para usos internos no expuestos. |
| `backend/.../application/analyzer/AnalysisJobSubmissionService.java` | Modify | Refresca mensaje de la `RepositoryIntakeException`: `"Analysis queue is full"`; log cambia a `"queue ceiling reached"`. Estructura intacta. |
| `backend/.../application/analyzer/AnalyzeThrottleProperties.java` | Modify | Default `queueCapacity` 32 → 256. Validación `>= 1` ya existente. |
| `backend/.../infrastructure/persistence/analysis/jobs/AnalysisJobJpaRepository.java` | Modify | Spring Data: `int countByStatus(AnalysisJobStatus)`. `@Query` JPQL para `countQueuedAheadOf` (ver Interfaces). |
| `backend/.../infrastructure/persistence/analysis/jobs/JpaAnalysisJobRepository.java` | Modify | Implementa los dos nuevos métodos del port delegando en el `JpaRepository`. |
| `backend/.../infrastructure/web/analyzer/AnalysisJobStatusResponse.java` | Modify | Añade campo `QueueStateResponse queueState` (nullable) + record interno. |
| `backend/.../infrastructure/web/analyzer/AnalysisJobResponseMapper.java` | Modify | Firma cambia a `toStatus(AnalysisJobView)`. Emite `queueState` cuando `view.queueState() != null`. Override `phaseLabel = "Waiting for an analysis slot"` cuando `status=QUEUED && qs != null && qs.runningCount() >= qs.maxConcurrency()`. |
| `backend/.../infrastructure/web/analyzer/AnalysisJobController.java` | Modify | `getJob` llama a `queryService.getView(id)`. `submit` sin cambios. |
| `backend/.../infrastructure/config/AsyncExecutionConfig.java` | Modify | Refresca javadoc: `AbortPolicy` ahora protege el techo de cola, no la concurrencia. Sin cambios funcionales. |
| `backend/src/main/resources/db/migration/V7__analysis_job_queue_index.sql` | Create | `CREATE INDEX IF NOT EXISTS idx_analysis_job_status_created_at ON analysis_job (status, created_at);` |
| `frontend/src/types/api.ts` | Modify | Extiende `AnalysisJobStatusResponse` con `queueState?: { runningCount: number; maxConcurrency: number; queuePosition?: number | null }`. |
| `frontend/src/pages/DashboardPage.tsx` (`LoadingState`) | Modify | Bajo el bloque del stage actual, render condicional inline `{job?.queueState && job.status === 'QUEUED' && (<p>Position N · M of K</p>)}`. No nuevo helper. |

## Interfaces / Contracts

### Domain records

```java
public record AnalysisJobQueueState(
    int runningCount,
    int maxConcurrency,
    Integer queuePosition) {
  public AnalysisJobQueueState {
    if (runningCount < 0) throw new IllegalArgumentException("runningCount >= 0");
    if (maxConcurrency <= 0) throw new IllegalArgumentException("maxConcurrency >= 1");
    if (queuePosition != null && queuePosition < 1) {
      throw new IllegalArgumentException("queuePosition is 1-based");
    }
  }
}

public record AnalysisJobView(
    AnalysisJobSnapshot snapshot,
    AnalysisJobQueueState queueState) {}
```

### Repository port additions

```java
int countByStatus(AnalysisJobStatus status);

/** FIFO position (1-based) of a QUEUED job. Caller MUST hold a snapshot
 *  whose status == QUEUED; returns 1 + count of jobs with (createdAt, id)
 *  strictly less than the target. */
int countQueuedAheadOf(AnalysisJobId targetId);
```

### JPA query

```java
@Query("""
    select count(j) from AnalysisJobEntity j
    where j.status = 'QUEUED'
      and (j.createdAt < :createdAt
           or (j.createdAt = :createdAt and j.id < :id))
    """)
int countQueuedAheadOf(@Param("createdAt") Instant createdAt, @Param("id") UUID id);
```

`UUID` comparison: Postgres y H2 ordenan UUID lexicográficamente; basta como tie-break determinista. `JpaAnalysisJobRepository.countQueuedAheadOf(AnalysisJobId)` hace primero `findById` para obtener `createdAt+id`, devuelve `0` si el job ya no es QUEUED y delega al método anterior + suma 1.

### Web DTO

```java
public record AnalysisJobStatusResponse(
    String jobId,
    AnalysisJobStatus status,
    AnalysisJobPhase phase,
    String phaseLabel,
    int progressPercent,
    String message,
    UUID analysisId,
    JobErrorResponse error,
    JobMetricsResponse metrics,
    JobTimestampsResponse timestamps,
    QueueStateResponse queueState) {

  public record QueueStateResponse(
      int runningCount, int maxConcurrency, Integer queuePosition) {}
  /* ... existing nested records unchanged ... */
}
```

### Frontend type

```ts
export interface AnalysisJobQueueStateResponse {
  runningCount: number
  maxConcurrency: number
  queuePosition?: number | null
}

export interface AnalysisJobStatusResponse {
  /* ... */
  queueState?: AnalysisJobQueueStateResponse | null
}
```

### Flyway V7

```sql
-- V7__analysis_job_queue_index.sql
-- Speeds up:
--   - WHERE status='RUNNING'                                 (countByStatus)
--   - WHERE status='QUEUED' AND created_at < ?               (countQueuedAheadOf)
-- Postgres + H2 both honour multi-column btree (status, created_at).
CREATE INDEX IF NOT EXISTS idx_analysis_job_status_created_at
    ON analysis_job (status, created_at);
```

No usamos `CREATE INDEX CONCURRENTLY` (tabla pequeña; `executeInTransaction=false` añade complejidad innecesaria). Documentar en commit que si en producción la tabla crece >1M filas, una migración futura puede reconstruirlo `CONCURRENTLY`.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|--------------|----------|
| Domain | Invariantes de `AnalysisJobQueueState` (queuePosition >= 1, runningCount >= 0). | Test JUnit puro. |
| Application | `AnalysisJobQueryService.getView` con stub de repo. | `AnalysisJobQueryServiceTest.getView_includesQueueStateForQueuedJob`, `getView_omitsQueuePositionForRunningJob`, `getView_omitsQueueStateForTerminalJob`. |
| Persistence | Métodos nuevos sobre H2 (perfil test). | `JpaAnalysisJobRepositoryTest.countQueuedAheadOf_returnsFifoPosition` (siembra 4 QUEUED con `createdAt` distintos, valida posiciones 1..4) y `countByStatus_returnsExpected`. |
| Web | DTO mapping + phaseLabel override. | `AnalysisJobControllerTest.getJob_includesQueueStateWhenQueued`, `getJob_phaseLabelWaitingWhenSlotContention`. |
| Web (migración) | Renombrar `AnalysisJobControllerTest.postWhenExecutorSaturatedReturns429RateLimited` → `postWhenQueueCeilingReachedReturns429`. Setup con `queueCapacity=1, maxConcurrent=1` y latch que bloquee el worker. |
| Application (migración) | Renombrar `AnalysisJobSubmissionServiceTest.rejectsWith429AndRollsBackRowWhenExecutorIsSaturated` → `rejectsWith429WhenQueueCeilingReached`. Mantiene assertion de rollback. |
| Integration | Cap real + promoción. | `AnalysisJobExecutionServiceConcurrencyIT.runningCountNeverExceedsMaxConcurrency` (Awaitility, `maxConcurrent=2`, 5 jobs, observa que `countByStatus(RUNNING) <= 2` en toda la corrida) y `failedJobReleasesSlotForNextQueued` (simula fail del primer RUNNING, verifica que un QUEUED sube a RUNNING). |
| Frontend | Hook surface. | `useAnalysisJob.test.ts`: añadir caso "expone queueState cuando el backend lo emite". |
| Frontend (opcional) | Render del subtítulo "Position N · M of K". | Diferido: si el setup de `LoadingState.test.tsx` requiere mock de fetch/timer global, basta el test del hook. |

### Observability

Cada transición ya emite logs estructurados con `jobId`/`phase`/`progressPercent` (TKM-43). Sin nuevas métricas obligatorias. Como mejora futura — fuera de scope — Micrometer gauges `analysis_jobs.running.count` y `analysis_jobs.queued.count` derivados de `countByStatus`.

## Migration / Rollout

1. Merge en `main` aplica V7 al arranque (Flyway `migrate`). Índice condicional `IF NOT EXISTS` → re-ejecución idempotente.
2. Sin feature flag: el cambio de semántica (`429 → 202 QUEUED`) es softening del contrato y queda documentado en `docs/API.md` + changelog del PR.
3. Smoke manual: con `tokenmeter.analyze-throttle.max-concurrent=1` en `application-local.yml`, lanzar 3 `curl POST /api/analyze` consecutivos contra repos pequeños; el 2º y 3º deben devolver `202 QUEUED`. Pollear los 3 `jobId`: el 2º debe ver `queuePosition=1`, el 3º `queuePosition=2`, ambos `runningCount=1, maxConcurrency=1, phaseLabel="Waiting for an analysis slot"`. Tras completar el primero, `queuePosition` del 2º desaparece (status → RUNNING) y el 3º pasa de `2` a `1`.
4. Rollback: revertir merge + `DROP INDEX IF EXISTS idx_analysis_job_status_created_at;`. Sin pérdida de datos (índice y campos JSON opcionales).

## Risks & Mitigations

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Cambio de contrato `429 → 202 QUEUED` rompe clientes que reintentaban en 429 | Medium | Documentar en `docs/API.md` y `docs/ARCHITECTURE.md`. El 429 sigue existiendo (techo de cola + rate limiter por IP). Softening de contrato — clientes que ignoraban el 429 no se ven afectados. |
| `queuePosition` fluctúa entre polls (no monotónico estricto si un job de delante falla y libera slot) | Medium | Comentario en DTO + entrada en `docs/API.md` etiquetando el campo como "best-effort estimate, monotonic decreasing en happy path". UI no asume monotonía. |
| Crecimiento ilimitado de filas QUEUED | Medium | `queueCapacity=256` + `AbortPolicy` residual mantienen techo finito. Test `postWhenQueueCeilingReachedReturns429` cubre el borde. |
| Coste de `count(*)` por polling con cola larga | Low | Índice V7 `(status, created_at)`. A 256 filas + index range scan, latencia << 1 ms. |
| Race `markStarted` vs poll inmediato puede mostrar `status=QUEUED, runningCount=N` por una ventana de milisegundos | Low | Heredado de TKM-43. UI muestra "Waiting for slot" durante un poll y al siguiente ya RUNNING. Aceptable. |
| Tests TKM-43 (`rejectsWith429...`, `postWhenExecutorSaturatedReturns429RateLimited`) quedan obsoletos | Medium | Renombrados con setup explícito (`queueCapacity=1, maxConcurrent=1`) para forzar el techo. Cobertura del 429 preservada. |
| `phase=QUEUED, runningCount<maxConcurrency` puede aparecer brevemente al inicio (ventana antes de `markStarted`) — el mapper devolvería `phaseLabel="Queued"` aunque vaya a ejecutarse de inmediato | Low | Aceptable: el label cambia al siguiente poll (1.5 s). Documentar el branch en comentario del mapper. |

## Open Questions

- [ ] Ninguna. Todas las decisiones del exploration están cerradas y todos los nombres (paquetes, métodos, columnas, índices, archivos de test) son explícitos.
