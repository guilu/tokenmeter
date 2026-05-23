# Exploration: concurrent-analysis-limits (TKM-44)

> Source ticket: Jira TKM-44 — "[QUEUE] Define and enforce concurrent analysis limits".
> Branch: `tkm-44-concurrent-analysis-limits`. Builds on TKM-43 (`observable-analysis-jobs`), archived 2026-05-23.

## Goal (one paragraph)

Convertir el límite de concurrencia ya cableado en TKM-43 en una **cola real visible al usuario**: en vez de devolver `429 RATE_LIMITED` cuando los N workers están ocupados, el backend admite el job (`202 Accepted`, `status=QUEUED`) y lo promueve a `RUNNING` cuando hay slot. Hacer observable `queuePosition`, `runningCount` y `maxConcurrency` vía `GET /api/analyze/jobs/{jobId}` y reflejarlo en el panel existente de `LoadingState` con la fase "Queued" / "Waiting for an analysis slot".

## Current State (post-TKM-43, master `b2be0a2`)

### Submission path

`POST /api/analyze` → `AnalysisJobController.submit` → `AnalysisJobSubmissionService.submit(rawUrl)`:

1. Parse URL (`GitHubRepositoryUrl.parse`). Falla → `400 INVALID_URL`, sin insertar fila.
2. Construye `AnalysisJobSnapshot` inicial con `status=QUEUED`, `phase=QUEUED`, `progress=0`, `message="Job queued"`, `createdAt=now`.
3. `jobRepository.save(initial)` → fila persistida especulativamente.
4. `executor.execute(() -> executionService.runJob(id))`.
   - Si `RejectedExecutionException` (incluye `TaskRejectedException` de Spring) → `jobRepository.deleteById(id)` y `RepositoryIntakeException(RATE_LIMITED)` → handler global → `429`.
5. Devuelve snapshot persistido → controller mapea a `AnalysisJobAcceptedResponse(jobId, status, statusUrl, analysisId=null)` → `202`.

### Executor wiring (`AsyncExecutionConfig`)

```
analysisJobExecutor: ThreadPoolTaskExecutor
  corePoolSize    = maxPoolSize = tokenmeter.analyze-throttle.max-concurrent      (default 3)
  queueCapacity   = tokenmeter.analyze-throttle.queue-capacity                    (default 32)
  rejectionPolicy = AbortPolicy   ← este es el origen del 429
  threadNamePrefix = "tm-job-"
  waitForTasksToCompleteOnShutdown = true, awaitTerminationSeconds = 30
```

Por la semántica de `ThreadPoolExecutor` con `core = max`, **el queue interno (LinkedBlockingQueue acotada por `queueCapacity`) ya hace de cola FIFO de promoción**. Cuando un worker libera su slot, toma la siguiente tarea de la cola. Esto es exactamente lo que TKM-44 pide funcionalmente; lo único que falta es:

- **No rechazar** con `AbortPolicy` (en su lugar mantener la fila `QUEUED` y dejar que la tarea espere en la cola interna del executor).
- **Subir el techo** de `queueCapacity` a algo holgado pero finito (para no crecer la BD sin límite).
- **Exponer** `queuePosition`, `runningCount`, `maxConcurrency` en el snapshot.

### Status / phase lifecycle

- `AnalysisJobStatus`: `QUEUED → RUNNING → {SUCCESS | FAILED}`. `SUCCESS` y `FAILED` terminales.
- `AnalysisJobPhase`: `QUEUED, CHECKING_CACHE, CLONING_REPOSITORY, SCANNING_FILES, FILTERING_FILES, COUNTING_TOKENS, CALCULATING_COSTS, SAVING_REPORT, COMPLETED, FAILED`.
- Transición `QUEUED → RUNNING` ocurre **dentro de `JpaAnalysisJobProgressEmitter.transition(...)`** la primera vez que se llama (con phase `CHECKING_CACHE`, progress 5). Esto significa que mientras el job está en la cola del executor sin worker libre, **realmente sigue `status=QUEUED, phase=QUEUED`**, lo cual es perfecto para TKM-44.
- `AnalysisJobReaper` (`ApplicationRunner @Order(0)`) marca como `FAILED/JOB_INTERRUPTED` todas las filas no-terminales al arranque → cubre tanto `QUEUED` como `RUNNING` huérfanos.
- `AnalysisJobRetentionScheduler` purga solamente filas con `completedAt < cutoff` y `status ∈ {SUCCESS, FAILED}` (filtro vía `findByStatusAndCompletedAtBefore`) — las filas `QUEUED`/`RUNNING` están a salvo del cleanup.

### Persistence

- Tabla `analysis_job` (V6). Check constraints relevantes:
  - `chk_analysis_job_status` ∈ `QUEUED|RUNNING|SUCCESS|FAILED`.
  - `chk_analysis_job_phase` ∈ las 10 fases.
  - `chk_analysis_job_progress_invariant`: `progress ≤ 99 OR (status=SUCCESS AND analysis_id IS NOT NULL)`. Con `progress=0` en `QUEUED` se cumple.
  - `chk_analysis_job_terminal_completed`: `(status IN ('SUCCESS','FAILED')) = (completed_at IS NOT NULL)` — `QUEUED` con `completed_at=NULL` cumple.
- Índices: `idx_analysis_job_status`, `idx_analysis_job_created_at`, `idx_analysis_job_completed_at`. **`(status, created_at)` no existe** — `queuePosition` que cuente `WHERE status='QUEUED' AND created_at < ?` debería estar bien con el índice de `created_at` + filtro pequeño, pero conviene evaluar un índice compuesto si la cola crece.
- `AnalysisJobJpaRepository` expone `findByStatusIn(...)` y `findByStatusAndCompletedAtBefore(...)`. No hay aún ni `countByStatus(...)` ni "queue position" query.

### Response shape

`AnalysisJobStatusResponse` actual:

```
{ jobId, status, phase, phaseLabel, progressPercent, message, analysisId,
  error{code,message}?, metrics{...}, timestamps{...} }
```

No tiene `queuePosition`, `runningCount`, `maxConcurrency`. Habrá que añadirlos (todos opcionales / nullables para preservar la spec actual y los clientes que ya parsean el JSON).

`AnalysisJobResponseMapper.PHASE_LABELS` mapea `QUEUED → "Queued"`. TKM-44 sugiere "Waiting for an analysis slot" cuando hay contención; podemos elegir entre cambiar el label estático o derivarlo del backend según `runningCount >= maxConcurrency`.

### Rate-limit interceptor (sigue activo)

`AnalyzeRateLimitInterceptor` está aplicado a `POST /api/analyze` y `POST /api/repositories/intake` (excluye `/api/analyze/jobs/**` y `/api/analyze/*`). Sigue siendo un límite **por IP** (5 req/min default), **no** un límite de concurrencia global. **No** se elimina con TKM-44: aún tiene sentido como anti-abuso por IP. Solo desaparece el segundo 429 que provenía de `AbortPolicy` en el executor.

### Frontend

- `useAnalysisJob` (polling 1.5 s, chain `setTimeout`, abortcontroller). No tiene rama especial para `QUEUED`; lo trata como cualquier otro estado no-terminal.
- `analysisJobProgress.ts` mapea `QUEUED → stage 0`. `progressFromJob` clampa a 99 mientras no haya `SUCCESS + analysisId`.
- `LoadingState` (en `DashboardPage.tsx`): renderiza `phaseLabel` directamente del snapshot. **Si el backend envía `phaseLabel="Waiting for an analysis slot"` el componente lo muestra tal cual sin cambios estructurales.**
- `JobStatus` en `types/api.ts` ya incluye `'QUEUED'`. No hay enum nuevo que añadir cliente para el status — solo nuevos campos opcionales en `AnalysisJobStatusResponse`.

### Pruebas existentes a tener en cuenta (van a cambiar)

- `AnalysisJobSubmissionServiceTest.rejectsWith429AndRollsBackRowWhenExecutorIsSaturated` — escenario que **deja de aplicar** con la nueva semántica (saturación = QUEUED, no 429). Mover a un nuevo test "se acepta como QUEUED cuando los workers están llenos" + mantener un test residual para el **techo de cola** (donde sí se sigue devolviendo 429).
- `AnalysisJobControllerTest.postWhenExecutorSaturatedReturns429RateLimited` — idem.
- `AnalysisJobControllerTest.repeatedPollingNeverHits429BecauseInterceptorIsExcluded` — se mantiene tal cual.
- `AnalyzeRateLimitInterceptorTest` — sin cambios.

### Specs y docs a tocar (delta)

- `openspec/specs/analysis-jobs/spec.md`:
  - `Requirement: Asynchronous Submission Contract`: el escenario *Saturated executor rejects synchronously* invierte sentido → la saturación de slots admite QUEUED. Se mantiene una variante "queue ceiling" donde sí se devuelve 429.
  - `Requirement: Job Observability Endpoint`: añadir campos opcionales `queuePosition`, `runningCount`, `maxConcurrency` y escenarios de polling de un job en cola.
  - Nuevo `Requirement: Concurrency Cap and Queue Promotion` (núcleo de TKM-44).
- `docs/API.md` líneas 33, 116, 120, 234: redefinir cuándo se devuelve `429 RATE_LIMITED` (sólo por rate limiter IP o cola interna llena a partir del nuevo techo). Documentar nuevos campos en la respuesta de `GET /api/analyze/jobs/{jobId}`.
- `docs/ARCHITECTURE.md` líneas 96, 145, 171, 183-186, 200, 420: redibujar el diagrama del flujo de saturación, actualizar la tabla de executor wiring y la sección 6 del recap final.

## Decisions / open questions

| # | Question | Tentative answer (closes in propose) |
|---|---|---|
| 1 | ¿Cola del executor (`LinkedBlockingQueue` interna) o dispatcher propio que sondea la BD? | **Cola del executor** (Opción A). FIFO ya garantizado y promoción al liberar worker es automática. Mínimo delta. |
| 2 | ¿1-based o 0-based para `queuePosition`? | **1-based** (la persona en la posición 1 será la siguiente en ejecutarse). Más natural para UI ("Tu sitio en la cola: 3"). |
| 3 | ¿Se almacena `queuePosition` o se computa on read? | **On read**. `SELECT count(*) FROM analysis_job WHERE status='QUEUED' AND created_at < :createdAt` + 1. Es un valor inherentemente volátil; cachearlo daría drift. |
| 4 | ¿Cómo se calcula `runningCount`? | **On read**: `SELECT count(*) FROM analysis_job WHERE status='RUNNING'`. Una consulta más por polling es asumible (índice ya existe `idx_analysis_job_status`). |
| 5 | ¿`maxConcurrency` estático o dinámico? | **Estático**: leído de `AnalyzeThrottleProperties.maxConcurrent` al boot. Dinámico añade complejidad sin caso de uso claro. |
| 6 | ¿Eliminamos `AbortPolicy` por completo? | **No**. Mantener como red de seguridad con `queueCapacity` mucho más grande (p.ej. 256 default, configurable como `tokenmeter.analyze-throttle.max-queue`). Si la cola del executor llega a ese techo, sigue siendo `429 RATE_LIMITED` (lo documentamos como caso límite). Evita crecimiento sin freno y mantiene un contrato de "el sistema está realmente saturado". |
| 7 | ¿`phaseLabel` para `phase=QUEUED`? | **Computado por el mapper**: si `runningCount ≥ maxConcurrency` y `queuePosition >= 1` → "Waiting for an analysis slot"; si no, "Queued" (fase inicial antes de ser admitida al worker pool). Mantiene compatibilidad con clientes que solo leen el label. |
| 8 | ¿Nueva migración Flyway? | **No**. Toda la información (`status`, `created_at`) ya existe. Se valora un índice opcional `(status, created_at)` para acelerar `queuePosition` — quedará para `sdd-design` decidir si entra en V7 o se difiere. |
| 9 | ¿Promoción si `RUNNING` falla a mitad? | **Automática**: el worker thread termina (success o fail) → libera slot → executor pulls next. Garantizado por `ThreadPoolExecutor`. Reaper sigue cubriendo el caso "proceso muerto". |
| 10 | ¿Hace falta un watchdog para `RUNNING` zombies mid-run? | **No en este alcance**. El reaper cubre boot. Mid-run zombies (proceso vivo, thread bloqueado en `git clone`) ya se cortan por `tokenmeter.repository-intake.clone-timeout` (120 s). |
| 11 | ¿`runningCount` y `queuePosition` en la respuesta 202 también? | **No**, solo en el snapshot del polling endpoint. La 202 sólo confirma admisión. |
| 12 | ¿Coexistencia con retention? | **Sin cambios**. El scheduler ya filtra por `status ∈ {SUCCESS, FAILED} AND completed_at < cutoff`. Las filas QUEUED nunca se borran por retention (no tienen `completed_at`). |
| 13 | ¿Ordenación FIFO determinista? | **`ORDER BY created_at ASC, id ASC`** como tie-breaker. `id` es un UUIDv4 (random), suficiente para evitar empates. |
| 14 | ¿`AnalysisJobResponseMapper` debe leer del repositorio? | Sí — necesita inyectar `AnalysisJobRepository` (o un nuevo `AnalysisJobQueueMetricsService`) para calcular `runningCount` y `queuePosition`. Alternativa: el `QueryService` los calcula y los inyecta en un DTO de aplicación que el mapper traduce. Preferimos esta segunda opción para mantener la frontera hexagonal limpia. |
| 15 | ¿Hay que devolver `runningCount`/`maxConcurrency` también cuando `status=RUNNING`? | **Sí** (siempre que el snapshot no sea terminal). Aporta contexto si el usuario quiere comparar con cuántos otros corren a la vez. Cuando `status` es terminal estos campos pueden ser `null` (no aplica). |

## Approach options

### Option A — Lean on the executor's natural queue (recomendado)

- Cambiar `AbortPolicy` por dejarlo en `AbortPolicy` **con `queueCapacity` mucho mayor** (256 default). En la práctica, casi nunca se llega al techo y, cuando se llega, seguir devolviendo 429 es defensivo correcto.
- `AnalysisJobSubmissionService` mantiene la misma estructura. Cambia solo el comentario "Server is busy" → "Analysis queue full" para reflejar que ya no es por concurrencia sino por techo de cola.
- `AnalysisJobQueryService.findById` deja de devolver solo `AnalysisJobSnapshot` y empieza a devolver `AnalysisJobView` (snapshot + `queuePosition` + `runningCount` + `maxConcurrency`). El mapper traduce a JSON.
- Nuevos métodos en `AnalysisJobRepository`:
  - `int countByStatus(AnalysisJobStatus status)`
  - `int countQueuedBefore(Instant createdAt, UUID idTieBreaker)` (o `int queuePositionOf(AnalysisJobId id)`).
- Frontend: en `useAnalysisJob`/`LoadingState`, mostrar `phaseLabel` (ya soportado). Si `queuePosition != null` y `phase === 'QUEUED'`, añadir línea adicional `"Position #{queuePosition} of {queueLen}"` o similar, sin rediseñar.

**Pros**: mínima superficie de cambio, ningún hilo extra, ninguna migración nueva, FIFO garantizado por Java.
**Cons**: la fila `QUEUED` se queda en la BD aun cuando la tarea está esperando en memoria (no hay un solo source of truth) — pero esto ya pasa en TKM-43 entre la persistencia inicial y la transición a RUNNING; no es regresión.
**Effort**: Bajo-medio.

### Option B — Custom dispatcher con polling de BD

- Submission **no** llama a `executor.execute` directamente. Solo persiste `QUEUED`.
- Un `AnalysisJobDispatcher` con `@Scheduled` (cada 250-500 ms) ejecuta:
  - Cuenta `RUNNING`.
  - Si `RUNNING < maxConcurrent`, hace `SELECT ... WHERE status='QUEUED' ORDER BY created_at ASC LIMIT (maxConcurrent - runningCount) FOR UPDATE SKIP LOCKED` y los entrega al executor (que sería un `Executors.newCachedThreadPool` o similar, sin queue).
- Permite escalado horizontal trivial (varios pods con el mismo `SKIP LOCKED`).

**Pros**: source of truth único (BD), recuperación natural ante crash mid-run (cualquier instancia toma la fila siguiente), escalabilidad multi-instancia.
**Cons**: más complejo, requiere `SKIP LOCKED` y JPA con native query, riesgo de starvation si el dispatcher se duerme, latencia de hasta 500 ms extra para promocionar, una nueva clase con su propia ventana de tests. **TKM-44 es single-pod**: introducir esto sería sobreingeniería.

**Effort**: Medio-alto.

### Recommendation

**Opción A**. TKM-44 explícitamente dice "integrate with the same polling model from TKM-43" y "preserve current pipeline UI" — la opción A mantiene exactamente la misma arquitectura y solo invierte la rama de saturación, lo que minimiza el riesgo de regresión y de drift entre spec y código.

## Risks

- **Cambio de contrato observable**: clientes que dependían de `429 RATE_LIMITED` ante saturación recibirán ahora `202 + QUEUED`. Es un *softening* (lo que antes era rechazo ahora es aceptación), poco probable que rompa, pero hay que documentar en `docs/API.md` y en el changelog.
- **`queuePosition` flickering**: dos polls consecutivos pueden devolver posiciones distintas si entre medias se admiten nuevos jobs (la posición sólo decrece, salvo aborto/fallo) o si un job de delante falla y libera slot. UX-aceptable, pero documentar como estimación.
- **Crecimiento de filas QUEUED**: sin techo, una ráfaga indefinida llenaría la tabla. Mitigación: mantener `queueCapacity` (rebautizado conceptualmente como `max-queue`) razonable (256 default) + `AbortPolicy` residual → vuelve `429` cuando se rebasa.
- **Race entre `executor.execute` y `markStarted`**: ya existe en TKM-43. No se introduce nada nuevo.
- **Promoción en fallo**: si el worker que toma el QUEUED siguiente lanza una `Error` o `RuntimeException` no capturada antes de `runJobInternal`, la promoción podría romperse. `runJobInternal` ya envuelve todo en try/catch + `emitter.fail`; revisar que el wrapper en `runJob` también lo haga.
- **Tests existentes obsoletos**: dos tests asumen `RejectedExecutionException → 429`. Hay que renombrarlos/actualizarlos sin perder cobertura del nuevo borde (techo de cola).
- **Índice `(status, created_at)`**: si la cola crece a cientos de items, `count(*) WHERE status='QUEUED' AND created_at < ?` por polling puede empezar a costar. Diferir decisión a `sdd-design` (medir vs prevenir).
- **Persistencia especulativa**: si `executor.execute` cae después de un `RejectedExecutionException` en el caso "cola interna llena", la fila se borra. Pero ahora ya casi nunca se llega ahí. Mantener el rollback intacto.

## Affected Areas (file map)

### Backend — código

- `backend/src/main/java/dev/diegobarrioh/tokenmeter/application/analyzer/AnalysisJobSubmissionService.java` — refresco de comentarios y mensaje. La estructura no cambia.
- `backend/src/main/java/dev/diegobarrioh/tokenmeter/application/analyzer/AnalyzeThrottleProperties.java` — renombrar/duplicar `queueCapacity` con nuevo default (256) o introducir `maxQueue` con default 256 y mantener `queueCapacity` como alias deprecado para compat.
- `backend/src/main/java/dev/diegobarrioh/tokenmeter/application/analyzer/AnalysisJobQueryService.java` — devolver un nuevo `AnalysisJobView` con `queuePosition`, `runningCount`, `maxConcurrency` además del snapshot.
- `backend/src/main/java/dev/diegobarrioh/tokenmeter/application/analyzer/AnalysisJobRepository.java` — nuevos métodos `countByStatus`, `queuePositionOf` (o equivalente).
- `backend/src/main/java/dev/diegobarrioh/tokenmeter/domain/job/` — nuevo record `AnalysisJobQueueState(int queuePosition, int runningCount, int maxConcurrency)` o `AnalysisJobView(AnalysisJobSnapshot snapshot, AnalysisJobQueueState queue)`.
- `backend/src/main/java/dev/diegobarrioh/tokenmeter/infrastructure/config/AsyncExecutionConfig.java` — subir default `queueCapacity` (o introducir `maxQueue`). Mantener `AbortPolicy`. Documentar.
- `backend/src/main/java/dev/diegobarrioh/tokenmeter/infrastructure/persistence/analysis/jobs/AnalysisJobJpaRepository.java` — nuevos `@Query` o derived: `countByStatus`, `countByStatusAndCreatedAtLessThanEqual` (cuidando tie-break por id).
- `backend/src/main/java/dev/diegobarrioh/tokenmeter/infrastructure/persistence/analysis/jobs/JpaAnalysisJobRepository.java` — implementar los nuevos métodos del port.
- `backend/src/main/java/dev/diegobarrioh/tokenmeter/infrastructure/web/analyzer/AnalysisJobStatusResponse.java` — añadir `queuePosition`, `runningCount`, `maxConcurrency` (nullable).
- `backend/src/main/java/dev/diegobarrioh/tokenmeter/infrastructure/web/analyzer/AnalysisJobResponseMapper.java` — aceptar `AnalysisJobView`, mapear los nuevos campos; computar `phaseLabel="Waiting for an analysis slot"` cuando aplique.
- `backend/src/main/java/dev/diegobarrioh/tokenmeter/infrastructure/web/analyzer/AnalysisJobController.java` — sustituir uso de snapshot puro por view.

### Backend — config

- `backend/src/main/resources/application.yml` — actualizar default `queue-capacity` o añadir `max-queue: 256`.
- `backend/src/main/resources/application-prod.yml` / `application-docker.yml` — revisar si overrides existen.

### Backend — tests

- `AnalysisJobSubmissionServiceTest` — invertir test `rejectsWith429AndRollsBackRowWhenExecutorIsSaturated`: ahora "se acepta como QUEUED cuando todos los workers están ocupados y queda cola". Mantener un test separado para "cola llena de techo → 429".
- `AnalysisJobControllerTest.postWhenExecutorSaturatedReturns429RateLimited` — idem; nuevo test para `phaseLabel="Waiting for an analysis slot"` y campos `queuePosition/runningCount/maxConcurrency`.
- `AnalysisJobExecutionServiceTest` (si existe) — añadir test de "una promoción se dispara al fallar un RUNNING".
- Nuevo test integración (con `@SpringBootTest` ligero o concurrency harness) que verifique: con `maxConcurrent=2` y 4 submissions inmediatos, 2 quedan `RUNNING` y 2 `QUEUED`; al completar un `RUNNING`, uno de los `QUEUED` pasa a `RUNNING`.

### Frontend

- `frontend/src/types/api.ts` — extender `AnalysisJobStatusResponse` con `queuePosition: number | null`, `runningCount: number | null`, `maxConcurrency: number | null`.
- `frontend/src/pages/DashboardPage.tsx` (`LoadingState`) — mostrar como subtítulo o badge "Position {queuePosition}/{queueLength}" cuando `phase === 'QUEUED'` y `queuePosition != null`. Sin rediseño del componente.
- `frontend/src/utils/analysisJobProgress.ts` — sin cambios al stage mapping. Eventual ajuste a `progressFromJob` (que ya clampa) — no necesario.
- Tests: `useAnalysisJob.test.ts` y `analysisJobProgress.test.ts` no cambian; añadir test renderizado opcional para el subtítulo de posición en cola (si se decide cubrir UI con tests).

### Docs

- `docs/API.md` — sección de errores `RATE_LIMITED`: redefinir como "rate limiter por IP o techo de cola interna alcanzado". Documentar nuevos campos en el snapshot.
- `docs/ARCHITECTURE.md` — actualizar el diagrama y la tabla de `analysisJobExecutor`; sección 6 del recap final; tabla de mapeo de error codes.
- `CLAUDE.md` (raíz del repo) — añadir `TOKENMETER_MAX_CONCURRENT_ANALYSES` ya está; añadir variables nuevas si se introducen (p.ej. `TOKENMETER_ANALYZE_MAX_QUEUE`).

## Ready for Proposal

**Yes**. Las 15 preguntas tienen respuesta tentativa coherente. El cambio es bajo riesgo, mínima superficie, sin migración Flyway obligatoria, sin cambios en la frontera hexagonal y sin deps nuevas.

Siguiente fase recomendada: **`sdd-propose`** con el alcance descrito en *Option A* y las decisiones tentativas de la tabla.
