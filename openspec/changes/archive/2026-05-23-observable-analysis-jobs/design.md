# Design: Observable Analysis Jobs (TKM-43)

## Technical Approach

Convertimos `POST /api/analyze` en un **submission endpoint asíncrono**: persiste un job `QUEUED`, lo entrega a un `ThreadPoolTaskExecutor` dedicado, y devuelve `202` en < 100 ms. La pipeline existente (`clone → scan → tokenize → estimate → persist`) se reubica dentro de un `AnalysisJobExecutionService` que emite transiciones de fase y métricas a la nueva tabla `analysis_job` mediante un `AnalysisJobProgressEmitter` transaccional. El frontend reemplaza el timer simulado por un hook de polling que lee `GET /api/analyze/jobs/{jobId}` cada 1.5 s.

Mapeo a la spec:
- *Asynchronous Submission Contract* → `RepositoryAnalysisController#submit` + `AnalysisJobSubmissionService`.
- *Job Status / Phase / Progress Invariant* → enums de dominio + emitter con clamp + `CHECK` SQL.
- *Job Observability Endpoint* → `AnalysisJobController#getJob` (nuevo controller dedicado) excluido del rate-limiter.
- *Persisted Job State Survives Request Lifecycle* → tabla `analysis_job` + `AnalysisJobRetentionScheduler`.
- *Process Restart Reconciles Non-Terminal Jobs* → `AnalysisJobReaper` (`ApplicationRunner`).
- *Client Progress Invariants* → `useAnalysisJob` + `LoadingState` rewireado.

## Architecture Decisions

| # | Decisión | Alternativa rechazada | Justificación |
|---|----------|----------------------|---------------|
| 1 | **`@Async("analysisJobExecutor")` con `ThreadPoolTaskExecutor` dedicado** | DB-backed outbox (`SELECT FOR UPDATE SKIP LOCKED`), `@TransactionalEventListener`, broker externo | Una sola instancia, sin nuevas dependencias; alineado con `@EnableScheduling` ya presente; rejection mapea a 429 (paridad). Outbox o broker se reservan para multi-nodo. |
| 2 | **Tabla dedicada `analysis_job`** (no extender `analysis`) | Reutilizar `analysis` con columnas nullables | `analysis` tiene `NOT NULL` en `total_files`, `total_lines`, `total_bytes`, `token_encoding`, `total_tokens` (V2); aflojarlos rompe semántica del dominio existente. Una tabla nueva mantiene `analysis` como "resultado terminal" y permite TTL distinto. |
| 3 | **`UUID` para `analysis_job.id`, expuesto como string** | `Long` autogenerado | Consistencia con `analysis.id` (UUID), seguridad por opacidad, generable en aplicación sin round-trip. |
| 4 | **Columnas SQL para métricas (no JSONB)** | `metrics_json JSONB` | El reaper / retention scan sólo necesita leer status + completed_at; sin embargo, métricas en columnas (`smallint`/`bigint`) facilitan futuros leaderboards y validación de invariantes (`files_processed <= files_discovered`) vía `CHECK`. Espacio coste despreciable (≤ 6 enteros por fila). |
| 5 | **`AnalysisJobProgressEmitter` con `REQUIRES_NEW`** | Una sola tx larga; `JdbcTemplate` directo | `REQUIRES_NEW` garantiza que cada emit confirma antes de continuar, así el GET poll ve cambios. Mantenemos JPA (consistente con el resto del codebase). |
| 6 | **Controller nuevo `AnalysisJobController`** | Añadir métodos al `RepositoryAnalysisController` existente | `RepositoryAnalysisController` ya tiene 8 endpoints y mezcla HTML + JSON + PNG + SVG; un controller dedicado mantiene el SRP y simplifica el exclusion del interceptor (`addPathPatterns` deja `/api/analyze/jobs/**` fuera). |
| 7 | **`queue-capacity = 32` (configurable, default 32)** | `0` (paridad estricta con hoy) | El estado `QUEUED` existe en el spec → debe haber cola. 32 es ~10× `max-concurrent=3`. `AbortPolicy` al saturar mapea a 429 → mantiene paridad con el comportamiento actual cuando se llena de verdad. |
| 8 | **Reaper en `ApplicationRunner` + `@Scheduled` de retención** | Heartbeats + reaper periódico | Para v1 una sola instancia el `ApplicationRunner` cubre crashes; heartbeats añaden complejidad sin caso de uso. Spec sólo exige reconciliación al boot. |
| 9 | **Vitest + RTL + jsdom como devDeps** | Smoke manual | Spec exige 3 scenarios del cliente (SUCCESS, FAILED, clamp ≤ 99). Sin vitest, no son verificables en CI. Coste: ~10 MB devDeps. |
| 10 | **Pipeline instrumentada por wrappers, no por callbacks en servicios** | Inyectar `Consumer<Progress>` en `RepositoryFileScanner`/`RepositoryTokenizationService` | Mantiene dominio puro; el job emite progreso al entrar/salir de cada paso desde `AnalysisJobExecutionService`. Granularidad fina dentro de `COUNTING_TOKENS` se logra envolviendo el bucle en el servicio de ejecución. |

## Data Flow

```
HTTP POST /api/analyze
    │
    ▼
RepositoryAnalysisController#submit (hilo HTTP, < 100 ms)
    │
    ├─ GitHubRepositoryUrl.parse           → 400 INVALID_URL si falla
    ├─ AnalysisJobSubmissionService#submit
    │     ├─ persistInitialJob(QUEUED)     [tx CREATE]
    │     └─ analysisJobExecutor.execute(  → 429 RATE_LIMITED si rechazo (AbortPolicy)
    │           () -> executionService.runJob(jobId))
    │
    └─ 202 { jobId, status, statusUrl, analysisId:null }

analysisJobExecutor (thread tm-job-N)
    │
    ▼
AnalysisJobExecutionService#runJob(jobId)
    │  emitter.transition(QUEUED→CHECKING_CACHE)     [REQUIRES_NEW]
    │  emitter.transition(CHECKING_CACHE→CLONING)
    │      cloner.clone(...)
    │  emitter.transition(→SCANNING_FILES)
    │      sizeCalculator + scanner.scan()
    │      emitter.metric(filesDiscovered=N)
    │  emitter.transition(→COUNTING_TOKENS)
    │      tokenizer.tokenize(...)  (loop con throttled emits cada ~250 ms)
    │      emitter.metric(filesProcessed, tokensCounted)
    │  emitter.transition(→CALCULATING_COSTS)
    │      estimator.estimate(...)
    │      emitter.metric(pricingModelsProcessed)
    │  emitter.transition(→SAVING_REPORT)
    │      persistence.save(...)  → analysisId
    │  emitter.success(analysisId)  [progress=100, status=SUCCESS, phase=COMPLETED]
    │
    └─ catch RepositoryIntakeException → emitter.fail(code, msg)
       catch Throwable                  → emitter.fail(ANALYSIS_FAILED, msg)
       finally                          → workspace cleanup (existing)

HTTP GET /api/analyze/jobs/{jobId}  (no rate-limiter)
    │
    ▼
AnalysisJobController#getJob → AnalysisJobQueryService.snapshot(id)
    └─ 200 AnalysisJobStatusResponse | 404
```

## File Changes

### Domain (`domain/job/`)

| File | Action | Description |
|------|--------|-------------|
| `domain/job/AnalysisJobId.java` | Create | `record AnalysisJobId(UUID value)`. Factory `random()`. |
| `domain/job/AnalysisJobStatus.java` | Create | `enum { QUEUED, RUNNING, SUCCESS, FAILED }` con `boolean isTerminal()`. |
| `domain/job/AnalysisJobPhase.java` | Create | `enum { QUEUED, CHECKING_CACHE, CLONING_REPOSITORY, SCANNING_FILES, FILTERING_FILES, COUNTING_TOKENS, CALCULATING_COSTS, SAVING_REPORT, COMPLETED, FAILED }` con `int ordinalForProgress()` y `boolean canTransitionTo(AnalysisJobPhase)`. |
| `domain/job/AnalysisJobMetrics.java` | Create | `record AnalysisJobMetrics(Long filesDiscovered, Long filesProcessed, Long filesSkipped, Long tokensCounted, Integer contextWindows, Integer pricingModelsProcessed)` — todos nullable, validación non-negative en el canonical constructor. |
| `domain/job/AnalysisJobSnapshot.java` | Create | `record` con `id`, `status`, `phase`, `progressPercent`, `message`, `analysisId` (UUID nullable), `errorCode`, `errorMessage`, `metrics`, `createdAt`, `startedAt`, `updatedAt`, `completedAt`. |
| `domain/job/AnalysisJobErrorCode.java` | Create | `enum { CLONE_TIMEOUT, REPOSITORY_TOO_LARGE, INVALID_URL, ANALYSIS_FAILED, JOB_INTERRUPTED, RATE_LIMITED }`. Mapeo desde `RepositoryIntakeErrorCode` en el emitter. |
| `domain/job/package-info.java` | Create | Documentación del paquete. |

### Application (`application/analyzer/`)

| File | Action | Description |
|------|--------|-------------|
| `application/analyzer/AnalysisJobRepository.java` | Create | Port: `save(initialQueued)`, `markStarted(id, instant)`, `updatePhase(id, phase, progressPercent, message)`, `updateMetrics(id, metrics)`, `markSuccess(id, analysisId, instant)`, `markFailed(id, code, message, instant)`, `markInterrupted(id, instant)`, `findById(id)`, `findStaleAt(status, olderThan)`, `findNonTerminal()`, `deleteCompletedBefore(status, instant)`. |
| `application/analyzer/AnalysisJobProgressEmitter.java` | Create | Interface en `application/` con `transitionPhase`, `updateMetrics`, `success`, `fail`. Implementación `JpaAnalysisJobProgressEmitter` en `infrastructure/persistence/` con `@Transactional(propagation = REQUIRES_NEW)` en cada método. Aplica el clamp `progressPercent ≤ 99` salvo en `success`. |
| `application/analyzer/AnalysisJobSubmissionService.java` | Create | Punto de entrada sync. Valida URL, llama `executor.execute(...)` (AbortPolicy → `RepositoryIntakeException(RATE_LIMITED)` capturado por el handler existente → 429), persiste `QUEUED` antes del submit; rollback si `RejectedExecutionException`. |
| `application/analyzer/AnalysisJobExecutionService.java` | Create | `@Async("analysisJobExecutor")` `runJob(AnalysisJobId)`. Orquesta la pipeline llamando al emitter por fase. Cleanup del temp dir en `finally` (preservar comportamiento actual). |
| `application/analyzer/AnalysisJobQueryService.java` | Create | `Optional<AnalysisJobSnapshot> findById(AnalysisJobId)`. Sin @Transactional (read-only via repo). |
| `application/analyzer/AnalysisJobReaper.java` | Create | Implementa `ApplicationRunner`. Llama `repository.findNonTerminal()` y para cada uno `markInterrupted` (status=FAILED, phase=FAILED, errorCode=JOB_INTERRUPTED, message="Analysis interrupted by service restart"). |
| `application/analyzer/AnalysisJobRetentionScheduler.java` | Create | `@Scheduled(cron = "${tokenmeter.analyze-throttle.retention.cron:0 30 3 * * *}")`. Llama `deleteCompletedBefore(SUCCESS, now - retention.success)` y `(FAILED, now - retention.failed)`. |
| `application/analyzer/AnalyzeThrottleProperties.java` | Modify | Añadir `int queueCapacity` (default 32), `Retention retention` con `Duration success` (P7D) y `Duration failed` (P30D), `String retentionCron` (default `"0 30 3 * * *"`). Backwards-compatible (canonical constructor con defaults). |
| `application/analyzer/RepositoryAnalysisService.java` | Modify | Eliminar el método `analyze(String)` síncrono (sólo `findById(UUID)` queda). El cuerpo de pipeline se reubica en `AnalysisJobExecutionService` reutilizando los mismos colaboradores (`cloner`, `properties`, `fileScanner`, `tokenizationService`, `persistenceService`, `costEstimationService`, `sizeCalculator`). `AnalysisConcurrencyGuard` deja de usarse aquí (la cola del executor reemplaza el semáforo); el bean se mantiene por compat hasta confirmar tests y se borra al final. |

### Infrastructure — persistence (`infrastructure/persistence/analysis/jobs/`)

| File | Action | Description |
|------|--------|-------------|
| `infrastructure/persistence/analysis/jobs/AnalysisJobEntity.java` | Create | `@Entity @Table(name = "analysis_job")`. Campos abajo. Sin Lombok. |
| `infrastructure/persistence/analysis/jobs/AnalysisJobJpaRepository.java` | Create | `JpaRepository<AnalysisJobEntity, UUID>` con derived queries: `List<AnalysisJobEntity> findByStatusIn(Collection<AnalysisJobStatus>)`, `List<AnalysisJobEntity> findByStatusAndCompletedAtBefore(AnalysisJobStatus, Instant)`. |
| `infrastructure/persistence/analysis/jobs/JpaAnalysisJobRepository.java` | Create | `@Component` adapter implementa `AnalysisJobRepository`. Mapeo entity ↔ domain. |
| `infrastructure/persistence/analysis/jobs/JpaAnalysisJobProgressEmitter.java` | Create | Implementación del emitter. Cada método `@Transactional(propagation = REQUIRES_NEW)`. Logs estructurados `jobId, phase, progressPercent`. |

`AnalysisJobEntity` campos (todos `private`):
```
UUID id (PK)
String repositoryUrl (varchar 500, not null)
@Enumerated(STRING) AnalysisJobStatus status (varchar 16, not null)
@Enumerated(STRING) AnalysisJobPhase phase (varchar 24, not null)
short progressPercent (smallint, not null)
String message (text, nullable)
String errorCode (varchar 64, nullable)
String errorMessage (text, nullable)
Long filesDiscovered, filesProcessed, filesSkipped, tokensCounted (bigint nullable)
Integer contextWindows, pricingModelsProcessed (int nullable)
UUID analysisId (FK → analysis.id, nullable)
Instant createdAt, updatedAt (not null)
Instant startedAt, completedAt (nullable)
```

### Infrastructure — web

| File | Action | Description |
|------|--------|-------------|
| `infrastructure/web/analyzer/AnalysisJobController.java` | Create | `@RestController` con `POST /api/analyze` y `GET /api/analyze/jobs/{jobId}`. El `POST` mueve aquí desde `RepositoryAnalysisController`. |
| `infrastructure/web/analyzer/RepositoryAnalysisController.java` | Modify | Eliminar `@PostMapping("/api/analyze")`. Resto (GET `/api/analyze/{id}`, cost-breakdown, badge, og-image, HTML público, leaderboards) intactos. |
| `infrastructure/web/analyzer/AnalysisJobAcceptedResponse.java` | Create | `record AnalysisJobAcceptedResponse(String jobId, AnalysisJobStatus status, String statusUrl, UUID analysisId)`. `analysisId` siempre `null` en esta capability. |
| `infrastructure/web/analyzer/AnalysisJobStatusResponse.java` | Create | `record AnalysisJobStatusResponse(String jobId, AnalysisJobStatus status, AnalysisJobPhase phase, String phaseLabel, int progressPercent, String message, UUID analysisId, JobErrorResponse error, JobMetricsResponse metrics, JobTimestampsResponse timestamps)` + sub-records. |
| `infrastructure/web/analyzer/AnalysisJobMapper.java` | Create | `toAccepted(jobId)` y `toStatus(snapshot)`. `phaseLabel` viene de un mapa estático `AnalysisJobPhase → String` (alineado con los 8 stages del frontend). |
| `infrastructure/web/WebMvcConfiguration.java` | Modify | El interceptor sólo se añade a `"/api/analyze"` (POST) y `"/api/repositories/intake"`. Hoy `addPathPatterns("/api/analyze")` ya coincide sólo con esa ruta exacta (no `/api/analyze/jobs/*` ni `/api/analyze/{id}`); confirmar añadiendo `.excludePathPatterns("/api/analyze/jobs/**", "/api/analyze/*")`. |

### Infrastructure — config

| File | Action | Description |
|------|--------|-------------|
| `infrastructure/config/AsyncExecutionConfig.java` | Create | `@Configuration @EnableAsync`. `@Bean("analysisJobExecutor") ThreadPoolTaskExecutor` con `corePoolSize = maxPoolSize = properties.maxConcurrent()`, `queueCapacity = properties.queueCapacity()`, `threadNamePrefix = "tm-job-"`, `setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy())`, `setWaitForTasksToCompleteOnShutdown(true)`, `setAwaitTerminationSeconds(30)`. |

### DB migrations

| File | Action | Description |
|------|--------|-------------|
| `backend/src/main/resources/db/migration/V6__analysis_job.sql` | Create | DDL abajo. |

```sql
CREATE TABLE analysis_job (
    id UUID PRIMARY KEY,
    repository_url VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL,
    phase VARCHAR(24) NOT NULL,
    progress_percent SMALLINT NOT NULL,
    message TEXT,
    error_code VARCHAR(64),
    error_message TEXT,
    files_discovered BIGINT,
    files_processed BIGINT,
    files_skipped BIGINT,
    tokens_counted BIGINT,
    context_windows INTEGER,
    pricing_models_processed INTEGER,
    analysis_id UUID REFERENCES analysis (id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    started_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_analysis_job_status CHECK (status IN ('QUEUED','RUNNING','SUCCESS','FAILED')),
    CONSTRAINT chk_analysis_job_phase CHECK (phase IN ('QUEUED','CHECKING_CACHE','CLONING_REPOSITORY','SCANNING_FILES','FILTERING_FILES','COUNTING_TOKENS','CALCULATING_COSTS','SAVING_REPORT','COMPLETED','FAILED')),
    CONSTRAINT chk_analysis_job_progress CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_analysis_job_progress_invariant CHECK (
        progress_percent <= 99 OR (status = 'SUCCESS' AND analysis_id IS NOT NULL)
    ),
    CONSTRAINT chk_analysis_job_files CHECK (
        files_processed IS NULL OR files_discovered IS NULL OR files_processed <= files_discovered
    ),
    CONSTRAINT chk_analysis_job_terminal_completed CHECK (
        (status IN ('SUCCESS','FAILED')) = (completed_at IS NOT NULL)
    ),
    CONSTRAINT chk_analysis_job_failed_payload CHECK (
        status <> 'FAILED' OR (error_code IS NOT NULL AND error_message IS NOT NULL)
    )
);

CREATE INDEX idx_analysis_job_status ON analysis_job (status);
CREATE INDEX idx_analysis_job_completed_at ON analysis_job (completed_at);
CREATE INDEX idx_analysis_job_created_at ON analysis_job (created_at);
```

### `application.yml`

```yaml
tokenmeter:
  analyze-throttle:
    max-concurrent: ${TOKENMETER_MAX_CONCURRENT_ANALYSES:3}
    queue-capacity: ${TOKENMETER_ANALYZE_QUEUE_CAPACITY:32}
    rate-limit-requests-per-window: ${TOKENMETER_RATE_LIMIT_REQUESTS:5}
    rate-limit-window-duration: ${TOKENMETER_RATE_LIMIT_WINDOW:60s}
    retention:
      success: ${TOKENMETER_JOB_RETENTION_SUCCESS:P7D}
      failed: ${TOKENMETER_JOB_RETENTION_FAILED:P30D}
      cron: ${TOKENMETER_JOB_RETENTION_CRON:0 30 3 * * *}
```

`PricingSchedulingConfig` ya activa `@EnableScheduling` cuando el feature flag de pricing está on. Para no atar la retención a esa condición, añadir un `@EnableScheduling` a `AsyncExecutionConfig` (Spring deduplica las anotaciones en distintas `@Configuration`).

### Frontend

| File | Action | Description |
|------|--------|-------------|
| `frontend/src/types/api.ts` | Modify | Añadir `JobStatus`, `JobPhase`, `AnalysisJobAcceptedResponse`, `AnalysisJobStatusResponse`, `JobMetrics`, `JobError`, `JobTimestamps`. |
| `frontend/src/services/api.ts` | Modify | Reemplazar `analyzeRepository`: ahora retorna `AnalysisJobAcceptedResponse`. Añadir `getAnalysisJob(jobId, signal?) → AnalysisJobStatusResponse`. Mantener `getAnalysis(id)`. |
| `frontend/src/hooks/useAnalysisJob.ts` | Create | Hook descrito abajo. |
| `frontend/src/pages/DashboardPage.tsx` | Modify | `handleSubmit` recibe `{ jobId }` y guarda `setActiveJobId`. `LoadingState` recibe `jobId` y consume `useAnalysisJob`. Eliminar `setInterval`, `liveStats` sintético y `analysisStages`-only avance por timer; las cards se bindean a `job.metrics`. Mapping `JobPhase → stageIndex` para conservar los 8 stages visuales (ver tabla abajo). `progress = Math.min(99, job.progressPercent)` salvo `SUCCESS + analysisId`. Al SUCCESS+analysisId, navegar (`window.history.pushState(null, '', analysisPath(analysisId))` + setRouteAnalysisId). Al FAILED, mostrar `toUserMessage({status, code, message})`. |
| `frontend/src/hooks/useAnalysisJob.test.ts` | Create | Tests vitest (ver §5). |
| `frontend/vitest.config.ts` | Create | Config jsdom. |
| `frontend/src/test/setup.ts` | Create | `import '@testing-library/jest-dom'`. |
| `frontend/package.json` | Modify | DevDeps `vitest@^2`, `@testing-library/react@^16`, `@testing-library/jest-dom@^6`, `@testing-library/dom@^10`, `jsdom@^25`. Scripts: `"test": "vitest run"`, `"test:watch": "vitest"`. |

Mapping `JobPhase → stage index` (sin reordenar los 8 stages existentes en `analysisStages`):

| Backend `phase` | Frontend `activeStage` | Label visible |
|---|---|---|
| `QUEUED` | 0 | Cloning repository |
| `CHECKING_CACHE` | 0 | Cloning repository |
| `CLONING_REPOSITORY` | 0 | Cloning repository |
| `SCANNING_FILES` | 1 | Detecting languages |
| `FILTERING_FILES` | 2 | Parsing files |
| `COUNTING_TOKENS` | 3 | Counting tokens |
| (sub-fase derivada de `metrics.contextWindows` no-null) | 4 | Building context windows |
| (sub-fase derivada de `metrics.pricingModelsProcessed` no-null) | 5 | Simulating AI workflows |
| `CALCULATING_COSTS` | 6 | Calculating pricing models |
| `SAVING_REPORT` | 7 | Generating estimates |
| `COMPLETED` | 7 | Generating estimates |
| `FAILED` | último visto | (overlay de error) |

## Interfaces / Contracts

### `AnalysisJobProgressEmitter` (puerto)

```java
public interface AnalysisJobProgressEmitter {
  void transition(AnalysisJobId id, AnalysisJobPhase phase, int progressPercent, String message);
  void updateMetrics(AnalysisJobId id, AnalysisJobMetrics metrics);
  void success(AnalysisJobId id, UUID analysisId, AnalysisJobMetrics finalMetrics);
  void fail(AnalysisJobId id, AnalysisJobErrorCode code, String message);
}
```

Reglas (impuestas por la impl + CHECK SQL):
- `transition` clampa `progressPercent` a `min(99, max(0, value))`.
- `success` es la única operación que persiste `progressPercent = 100`, `status = SUCCESS`, `phase = COMPLETED`, `completed_at = now`. El `CHECK chk_analysis_job_progress_invariant` rechaza intentos previos.
- `fail` setea `status = FAILED`, `phase = FAILED`, `completed_at = now`. Idempotente respecto a estados terminales (no-op si ya terminal).

### `AnalysisJobAcceptedResponse` (DTO)

```json
{ "jobId": "uuid", "status": "QUEUED|RUNNING", "statusUrl": "/api/analyze/jobs/{uuid}", "analysisId": null }
```

### `AnalysisJobStatusResponse` (DTO)

```json
{
  "jobId": "uuid",
  "status": "QUEUED|RUNNING|SUCCESS|FAILED",
  "phase": "QUEUED|...|COMPLETED|FAILED",
  "phaseLabel": "Counting tokens",
  "progressPercent": 0..100,
  "message": "string|null",
  "analysisId": "uuid|null",
  "error": { "code": "string", "message": "string" } | null,
  "metrics": {
    "filesDiscovered": 1234|null, "filesProcessed": 1234|null, "filesSkipped": 12|null,
    "tokensCounted": 9876|null, "contextWindows": 3|null, "pricingModelsProcessed": 12|null
  },
  "timestamps": { "createdAt": "iso", "startedAt": "iso|null", "updatedAt": "iso", "completedAt": "iso|null" }
}
```

### `useAnalysisJob` (hook)

```ts
export interface UseAnalysisJobResult {
  job: AnalysisJobStatusResponse | null
  isPolling: boolean
  error: ApiError | null
}

export function useAnalysisJob(jobId: string | null, intervalMs = 1500): UseAnalysisJobResult
```

Comportamiento:
- `setTimeout` encadenado (no `setInterval`) para evitar solapamiento.
- `AbortController` por request; `abort()` on unmount o cambio de `jobId`.
- Detiene polling si `status ∈ {SUCCESS, FAILED}` o si el último error tiene `status === 404` (job desconocido).
- Expone `error` (ApiError) cuando una respuesta no-OK / aborto no-Abort ocurre. Re-intenta tras error transitorio (status 5xx) hasta 3 veces con backoff `intervalMs * (n+1)`.

## Executor wiring details

`AsyncExecutionConfig`:

```java
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncExecutionConfig {

  @Bean(name = "analysisJobExecutor")
  public ThreadPoolTaskExecutor analysisJobExecutor(AnalyzeThrottleProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.maxConcurrent());
    executor.setMaxPoolSize(properties.maxConcurrent());
    executor.setQueueCapacity(properties.queueCapacity());
    executor.setThreadNamePrefix("tm-job-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
```

`AnalysisJobSubmissionService#submit` envuelve `executor.execute(...)` en try/catch:

```java
try {
  jobRepository.save(initialQueued);
  executor.execute(() -> executionService.runJob(jobId));
} catch (TaskRejectedException | RejectedExecutionException ex) {
  jobRepository.deleteById(jobId.value());   // no orphan QUEUED row
  throw new RepositoryIntakeException(
      RepositoryIntakeErrorCode.RATE_LIMITED,
      "Server is busy: analysis queue full. Please retry shortly.");
}
```

El `RepositoryIntakeExceptionHandler` ya mapea `RATE_LIMITED → 429`.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Domain unit | `AnalysisJobPhase.canTransitionTo`, `AnalysisJobMetrics` non-negative | JUnit puro, sin Spring. |
| Application unit | `AnalysisJobSubmissionService` (URL inválida → 400, executor saturado → 429, happy path → persist + execute), `AnalysisJobReaper` (RUNNING → FAILED/JOB_INTERRUPTED), `AnalysisJobRetentionScheduler` (clock fijo, verifica deletes). | Mockito; sin Spring. |
| JPA slice | `JpaAnalysisJobRepository` con H2 — verifica CHECK constraints (progress_percent>99 sin SUCCESS rechazado), índices, transición REQUIRES_NEW. | `@DataJpaTest`. |
| Controller | `AnalysisJobController` con `@WebMvcTest`: 202 con body shape, 400 INVALID_URL, 429 RATE_LIMITED, 200/404 en GET, ausencia de rate-limiter en GET (poll x10 sin 429). | `MockMvc`. |
| Integration | `RepositoryAnalysisServiceIntegrationTest` (existente) → migrar: invoca `submissionService.submit(url)` y espera (con `Awaitility`) a que `jobRepository.findById(...)` sea SUCCESS; aserciones sobre `AnalysisEntity` persistido. Mocks: `GitRepositoryCloner` con `Path` precreado. | Spring Boot test. |
| Frontend unit | `useAnalysisJob.test.ts`: (1) transiciona QUEUED→RUNNING→SUCCESS y deja de pollear; (2) FAILED expone `error`; (3) no expone `progressPercent === 100` hasta SUCCESS + analysisId (clamp). | Vitest + RTL + `vi.useFakeTimers()`. |
| E2E manual | `docker compose up` + repo público pequeño → ver progresión real en `LoadingState`. | Documentado en §9. |

## Migration of existing tests

- `RepositoryAnalysisServiceTest` → renombrar a `AnalysisJobExecutionServiceTest`. Reemplazar `service.analyze(url)` por `service.runJob(jobId)` previa creación del job `QUEUED` en un `AnalysisJobRepository` en memoria (fake o Mockito).
- `RepositoryAnalysisControllerTest` (POST) → migrar al nuevo `AnalysisJobControllerTest`. Aserciones cambian: `status().isAccepted()` (202), body con `jobId`/`statusUrl`. Conservar los GET tests del `RepositoryAnalysisControllerTest` original (cost-breakdown, og-image, badge, leaderboards) sin cambios.
- Tests existentes de `AnalysisConcurrencyGuard` se eliminan junto con su uso. Se cubre la concurrencia desde el lado del executor (rejection → 429).

## Observability

- Logging estructurado en cada `emitter.transition/updateMetrics/success/fail`: `MDC.put("jobId", id)` antes y `MDC.remove` después (helper `MdcScope` en `application/analyzer/`).
- Logs en `WARN` para `fail`, `INFO` para transiciones, `ERROR` para excepciones no esperadas.
- Micrometer (nice-to-have, opcional): `Counter` `tokenmeter.jobs.completed{status}` y `Timer` `tokenmeter.jobs.duration`. Si se omite, el management endpoint Prometheus ya expone métricas del executor (`spring.executor.*` cuando se nombra el bean — automático con `ThreadPoolTaskExecutor` registrado).

## Open Risks & Mitigations

| Riesgo | Mitigación (en código) |
|---|---|
| Pipeline corre en hilo HTTP por wiring `@Async` roto | Test `AnalysisJobControllerTest#submitReturnsImmediatelyEvenWhenExecutorTakesLong` mockea `executionService` para bloquear 5 s y exige `MockMvc` < 200 ms (asume `@Async` desviando). Además assert sobre `Thread.currentThread().getName()` en el job (debe empezar por `tm-job-`). |
| Poll no ve avance por transacción larga | `JpaAnalysisJobProgressEmitter` con `REQUIRES_NEW`; test JPA inserta job, emite transición en otra transacción y verifica visibilidad inmediata desde un `EntityManager` fresco. |
| Jobs huérfanos tras crash/restart | `AnalysisJobReaper`+ test integración (`@SpringBootTest` que pre-inserta RUNNING row antes de arrancar el runner). |
| Frontend muestra 100 % antes de `analysisId` | (1) CHECK SQL `chk_analysis_job_progress_invariant`. (2) Clamp en emitter. (3) Clamp `Math.min(99, ...)` en el hook hasta SUCCESS+analysisId. (4) Test vitest dedicado. |
| Crecimiento ilimitado de `analysis_job` | `AnalysisJobRetentionScheduler` + índice `idx_analysis_job_completed_at`. Test con clock simulado. |
| Tests existentes asumen retorno síncrono | Migración explícita listada en §7. |
| Race: `RejectedExecutionException` deja row `QUEUED` huérfana | Submission service borra el row dentro del catch antes de lanzar `RATE_LIMITED`. Cubierto por test unit. |
| Throttle de emit en `COUNTING_TOKENS` genera updates masivos | `AnalysisJobExecutionService` lleva un `lastEmitNanos` local y sólo invoca emit si `now - last >= 250ms` o si es el cierre de fase. |
| Borrado de `analysis` rompería invariante `progress=100 ⇒ analysis_id IS NOT NULL` | FK declarada como `ON DELETE RESTRICT`: ninguna fila de `analysis` referenciada por un `analysis_job` puede ser borrada. Mantiene la invariante incluso tras purgas de retención: cualquier job `SUCCESS` retiene su `analysis_id` no nulo hasta que el propio job se purga (lo que sucede antes por el TTL de 7 días vs. la ausencia de retención sobre `analysis`). |

## Migration / Rollout

- Migración aditiva V6. No requiere downtime ni feature flag.
- Cliente (frontend) y servidor se despliegan juntos en el mismo build de Docker (un solo container compose). No hay clientes externos que dependan del `200` síncrono.
- Smoke test plan manual:
  1. `docker compose up --build -d`.
  2. `curl -s -X POST localhost:8081/api/analyze -H 'content-type: application/json' -d '{"repositoryUrl":"https://github.com/guilu/tokenmeter"}'` → esperar 202 con `jobId`.
  3. `watch -n1 "curl -s localhost:8081/api/analyze/jobs/<jobId> | jq '.status,.phase,.progressPercent'"` → verificar progresión hasta `SUCCESS`/`100`.
  4. Abrir `http://localhost:3001`, lanzar análisis del mismo repo, ver el `LoadingState` consumiendo datos reales (no avance autónomo).
  5. `docker compose restart backend` mid-job; tras restart, `GET /api/analyze/jobs/<jobId>` debe devolver `FAILED/JOB_INTERRUPTED`.
- Rollback: ver `proposal.md §Rollback Plan`. La tabla queda; la app vuelve al binario anterior.

## Open Questions

- Ninguna pendiente que bloquee la implementación. Las decisiones de retención y queue-capacity quedan congeladas con sus defaults (P7D / P30D / 32) y son configurables vía variables de entorno.
