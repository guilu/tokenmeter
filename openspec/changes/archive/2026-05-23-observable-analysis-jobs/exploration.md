# Exploration: observable-analysis-jobs (TKM-43)

Ticket: TKM-43 — Run repository analyses as observable async jobs.
Goal: keep current AI Analysis Pipeline UI, but back it with a real backend job whose
state is polled. Never show 100% until a persisted analysis exists.

## Current State

`POST /api/analyze` ejecuta TODO el pipeline de forma síncrona dentro del hilo HTTP:

1. `RepositoryAnalysisController.analyze` (`infrastructure/web/analyzer/RepositoryAnalysisController.java:59-63`)
   delega directamente en `RepositoryAnalysisService.analyze(rawUrl)`.
2. `RepositoryAnalysisService.analyze` (`application/analyzer/RepositoryAnalysisService.java:77-104`):
   - Valida URL → `GitHubRepositoryUrl.parse`.
   - Adquiere semáforo `AnalysisConcurrencyGuard` (max 3 concurrentes,
     `tokenmeter.analyze-throttle.max-concurrent`, fairness=true). Si no hay permiso →
     `RepositoryIntakeException(RATE_LIMITED)` → HTTP 429.
   - Crea temp dir → `cloner.clone` (timeout `tokenmeter.repository-intake.clone-timeout`,
     120s por defecto).
   - `sizeCalculator.summarize` + `enforceSizeLimit` (max 300 MiB).
   - `RepositoryFileScanner.scan` (recorre árbol, salta `.git`/`node_modules`/…,
     `BinaryFileDetector`).
   - `RepositoryTokenizationService.tokenize` (loop por archivo, `OpenAiTokenCounter`).
   - `RepositoryCostEstimationService.estimate(totalTokens)` → N×3 estimaciones
     (N = nº modelos del `PricingProvider`).
   - `JpaAnalysisPersistenceService.save` → `@Transactional`, inserta `analysis`,
     `language_stats` y `cost_estimates`.
   - `finally` → libera semáforo y borra el temp dir.
3. Errores: cualquier `RepositoryIntakeException` se mapea en
   `RepositoryIntakeExceptionHandler` a su HTTP status; otras excepciones → 500
   `ANALYSIS_FAILED`. No queda registro persistente del intento fallido.
4. No existe "reutilización" / cache: cada `POST /api/analyze` re-clona aunque
   el mismo repo se haya analizado antes.

Capa de datos:

- Flyway V1–V5 aplicadas. `AnalysisStatus` enum sólo tiene `SUCCESS`
  (`infrastructure/persistence/analysis/AnalysisStatus.java`).
- `AnalysisEntity` (`infrastructure/persistence/analysis/AnalysisEntity.java`) ya
  tiene columna `status VARCHAR(40)` (V1), pero el código sólo guarda `SUCCESS`
  al final, jamás un row en estado intermedio o `FAILED`.
- Tabla `analysis` exige NOT NULL en `total_files`, `total_lines`, `total_bytes`,
  `token_encoding`, `total_tokens` → no es viable insertar el row "en progreso"
  reutilizando esta tabla sin migración intrusiva.

Frontend pipeline:

- `frontend/src/pages/DashboardPage.tsx:273-357` define `LoadingState`, que:
  - Avanza una etapa cada 1500 ms con `setInterval` (`activeStage`).
  - Calcula `progress` como `((activeStage+1)/N)*100` — puede llegar a 100% antes
    de tener `analysisId`.
  - Las 3 cards (Files inspected / Tokens sampled / Context windows) se generan
    con un seed determinístico a partir de la longitud del URL (`liveStats`
    `useMemo`) — son sintéticas, no provienen del backend.
  - La consola muestra `pipeline.run --repository <url>` y `stage.N: <label>`
    derivados del array `analysisStages` (8 entradas hardcoded).
- `handleSubmit` (líneas 120-141) llama `analyzeRepository(url)` y queda await
  hasta recibir el `RepositoryAnalysisResponse`; sólo entonces oculta el panel
  y empuja `analysisPath(id)` al history.
- `services/api.ts:33-48` hace `fetch('/api/analyze', POST, body)`; errores se
  parsean a `ApiError` con `code` (mapeo de mensajes UX en
  `toUserMessage` líneas 1233-1247).

Otros datos relevantes:

- `application.yml`: ya hay un management port (`:8090`) con Prometheus, así que
  añadir métricas Micrometer por jobs es trivial.
- `PricingSchedulingConfig` ya activa `@EnableScheduling` (condicional). No hay
  `@EnableAsync` en ningún sitio actualmente.
- `AnalyzeRateLimitInterceptor` aplica un rate-limit por ventana antes de llegar
  al controller — se mantendrá (el rate-limit por ventana es ortogonal al
  semáforo de concurrencia).

## Affected Areas

Backend:

- `infrastructure/web/analyzer/RepositoryAnalysisController.java` — cambia
  contrato de `POST /api/analyze` (rápido, devuelve `jobId`); añade
  `GET /api/analyze/jobs/{jobId}`.
- `infrastructure/web/analyzer/RepositoryAnalysisRequest.java` /
  `RepositoryAnalysisResponse.java` — nuevo DTO de respuesta async (`JobSubmissionResponse` con
  `jobId`, `status`, `statusUrl`, opcional `analysisId` para fast-path).
- `infrastructure/web/analyzer/RepositoryAnalysisMapper.java` — añadir mapper
  de Job → DTO.
- Nuevos DTO: `AnalysisJobResponse` con `jobId`, `status`, `phase`, `phaseLabel`,
  `progressPercent`, `message`, `submittedAt`, `startedAt`, `finishedAt`,
  `analysisId`, `error{code,message}`, `metrics{filesDiscovered,filesProcessed,
  filesSkipped,tokensCounted,contextWindows,pricingModelsProcessed}`.
- `application/analyzer/RepositoryAnalysisService.java` — refactorizar:
  - Método nuevo `submit(url) → JobSubmissionResult` que crea/persiste el job en
    `QUEUED`, lo encola, y retorna inmediatamente.
  - Método nuevo (package-private) `executeJob(jobId)` que corre la pipeline
    actualizando fases/métricas (esta es la parte que hoy está dentro de
    `analyze`, troceada por fases).
  - El `analyze(url)` síncrono actual se mantiene como helper interno o se
    elimina y se replantea como `submit + executeJob` ejecutado por un
    `Executor`.
- Nuevo modelo de dominio: `AnalysisJob` (record o entity) con `id`, `repositoryUrl`,
  `status`, `phase`, `progressPercent`, `metricsSnapshot`, `errorCode`, `errorMessage`,
  `analysisId` (FK opcional a `analysis.id`), `submittedAt`, `startedAt`,
  `finishedAt`, `lastHeartbeatAt`. Enums `JobStatus` y `JobPhase` viven en
  `domain/analyzer/`.
- Nuevo puerto: `application/analyzer/AnalysisJobRepository` (interfaz) con
  operaciones `save`, `update`, `findById`, opcional `findStaleRunning(...)`.
- Adapter JPA: `infrastructure/persistence/analysis/jobs/AnalysisJobEntity.java`
  + `AnalysisJobJpaRepository.java` + `JpaAnalysisJobRepository.java`.
- Nuevo `application/analyzer/AnalysisJobExecutor` (interface/impl) — wrap del
  `Executor` que invoca `executeJob`.
- Wiring: `@EnableAsync` o un `@Bean ExecutorService` explícito + `RejectedExecutionHandler`.
- Tracking de fases: `RepositoryFileScanner`, `RepositoryTokenizationService` y
  `RepositoryCostEstimationService` necesitan emitir progreso. Tres opciones:
  (a) callback funcional inyectado por job, (b) `ApplicationEventPublisher` con
  eventos por fase, (c) wrapping en el service nuevo sin tocar los servicios
  existentes (el job se limita a actualizar phase al entrar/salir de cada paso
  + un par de hooks para tokenización por archivos). Opción (c) minimiza el
  cambio y mantiene la pureza del dominio.
- Nueva migración Flyway `V6__analysis_jobs.sql` con tabla `analysis_job`
  (UUID PK, columnas indicadas, índices por `status`, `created_at`, posiblemente
  `repository_url`).
- `RepositoryIntakeErrorCode` — añadir `ANALYSIS_FAILED` (o reutilizar el código
  500 actual y guardarlo como `error_code` en el job; el mapeo HTTP del job
  poll es 200 con `status=FAILED`, no 500).
- `AnalyzeRateLimitInterceptor` se mantiene en `POST /api/analyze`. El
  `GET /api/analyze/jobs/{id}` NO debe estar tras el rate limiter (poll rápido)
  → revisar `WebMvcConfiguration` para asegurar que el interceptor sólo cubra
  el POST.

Frontend:

- `frontend/src/types/api.ts` — nuevos tipos `JobSubmissionResponse`,
  `AnalysisJobResponse`, enums `JobStatus`, `JobPhase`.
- `frontend/src/services/api.ts` — nuevo `submitAnalysis(url) → JobSubmissionResponse`,
  nuevo `getAnalysisJob(jobId) → AnalysisJobResponse`. Mantener `getAnalysis(id)`.
- `frontend/src/hooks/` — nuevo `useAnalysisJob(jobId)` que hace polling cada
  1.5s (intervalo configurable), corta en `SUCCESS`/`FAILED`, expone `job`,
  `error` y permite cancelar.
- `frontend/src/pages/DashboardPage.tsx` — `LoadingState` deja de avanzar por
  timer:
  - Recibe `job` del hook.
  - Mapea `job.phase` → uno de los 8 stages existentes (mapping nuevo).
  - `progress` proviene de `job.progressPercent` (clamp a 99 si
    status≠SUCCESS).
  - Cards usan `job.metrics.filesProcessed`, `job.metrics.tokensCounted`,
    `job.metrics.contextWindows`. Si faltan, mostrar `0` o `—`.
  - Consola usa `job.message` y/o `job.phase` real.
  - Cuando `status===SUCCESS && analysisId`, navegar a `/analysis/<id>` y
    `getAnalysis(analysisId)` para cargar el resultado (idéntico al flujo
    actual de "shared link").
  - Cuando `status===FAILED`, mostrar `error.message` con el mismo formato de
    `toUserMessage`.
- `handleSubmit` cambia: submit → recibe `jobId` → activa estado `polling` →
  el hook se encarga del resto.

Tests:

- Backend:
  - `RepositoryAnalysisServiceTest` (existente) — adaptar tras refactor.
  - Nuevo `AnalysisJobRepositoryTest` (slice JPA con H2).
  - Nuevo `AnalysisJobExecutorTest` (sin Spring, executor en línea).
  - `RepositoryAnalysisControllerTest` — añadir casos: submit devuelve
    `jobId`+`statusUrl`, GET de un job inexistente → 404, transición
    `QUEUED → RUNNING → SUCCESS` (mockeando el repo del job).
- Frontend (vitest si está disponible; si no, ad-hoc en tipos): polling
  `SUCCESS`, polling `FAILED`, no 100% antes de `SUCCESS`. Hoy el repo **no
  tiene vitest configurado** (sólo `tsc -b && vite build` + lint) — habrá
  que decidir en spec/design si añadir vitest o validar manualmente vía
  smoke. Una opción intermedia: testear el `useAnalysisJob` hook con
  vitest + jsdom, aceptando la nueva dependencia.

Documentación:

- `docs/API.md` y `README.md` — describir nuevo contrato async.
- `CLAUDE.md` — actualizar lista de endpoints si procede.

## Approaches

### Backend ejecutor (cómo correr el job fuera del hilo HTTP)

1. **Spring `@Async` + `ThreadPoolTaskExecutor` dedicado** — añadir
   `@EnableAsync` y un `@Bean` `analysisJobExecutor` (Executor) configurable:
   `core-size = max-concurrent`, `queue-capacity = N`,
   `RejectedExecutionHandler = AbortPolicy` para devolver 503 cuando satura.
   `executeJob` se marca `@Async("analysisJobExecutor")` o se invoca con
   `executor.execute(() → ...)`.
   - Pros: estándar Spring, observabilidad fácil (Micrometer expone metrics
     del executor), control de cola explícito, alineado con `@EnableScheduling`
     ya presente.
   - Cons: necesita disciplina con `MDC`/tracing (los logs cambian de hilo);
     hay que pasar `jobId` por parámetro y no por ThreadLocal.
   - Effort: Low.

2. **`ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`**
   El controller persiste el job `QUEUED`, publica `AnalysisJobSubmitted`, y un
   listener async maneja la ejecución.
   - Pros: separa el wiring; permite suscribir más listeners (métricas, logs).
   - Cons: indirección extra; igualmente necesitas un executor async detrás
     (`@Async`) y la observabilidad del executor; orden de eventos no
     determinista si hay varios listeners.
   - Effort: Medium.

3. **Cola DB-backed con poller (à la "outbox pattern")** — guardar el job y
   un `JobPoller` con `@Scheduled(fixedDelay = 200ms)` que toma `QUEUED` con
   `SELECT ... FOR UPDATE SKIP LOCKED`.
   - Pros: sobrevive a reinicios (un job perdido se recoge tras restart);
     escalable a múltiples instancias.
   - Cons: complejidad alta; latencia mínima de 200ms; requiere lógica de
     "lease/heartbeat" o reaper; sobra para una sola instancia.
   - Effort: High.

4. **Broker externo (RabbitMQ / Redis Streams / Spring Modulith Events Externalization)**
   - Pros: escalabilidad horizontal real.
   - Cons: nueva dependencia mayor; viola el principio "no añadir dependencias
     sin justificación clara" del CLAUDE.md.
   - Effort: High.

**Recomendación**: opción **1 (`@Async` + `ThreadPoolTaskExecutor` dedicado)**.
Es lo más alineado con la filosofía deliberadamente delgada del proyecto y
cubre todos los criterios de aceptación con una única instancia. Si en el
futuro queremos multi-instancia se puede migrar a la opción 3 sin romper el
contrato HTTP.

### Concurrencia y rate-limit

Mantener `AnalysisConcurrencyGuard` (semáforo). Dos sub-opciones:

- (a) Adquirir el permiso **antes** de devolver `jobId` (controller). Si no hay
  permiso → 429 antes de crear el job. La ejecución asume permiso ya tomado y
  lo libera al final. **Riesgo**: permisos amarrados a la cola del executor —
  si el executor encola pero el thread tarda, mantenemos el semáforo bloqueado
  más tiempo del necesario.
- (b) Crear el job siempre (`QUEUED`) y adquirir el permiso dentro de
  `executeJob`. Permite ver el job en `QUEUED` aunque el sistema esté saturado.
  Devolver `503/QUEUED` si la cola del executor está llena (`RejectedExecutionException`).
- **Recomendación**: (a) corto plazo, con `queue-capacity = 0` para que el
  rechazo sea inmediato (paridad con el comportamiento actual de 429). Si se
  acepta cola, optar por (b).

### Tracking de progreso

- **`progressPercent`** se calcula determinísticamente por fase:
  `QUEUED→0`, `CHECKING_CACHE→2`, `CLONING_REPOSITORY→10`,
  `SCANNING_FILES→30`, `FILTERING_FILES→40`,
  `COUNTING_TOKENS→40+50*(filesProcessed/filesDiscovered)`,
  `CALCULATING_COSTS→92`, `SAVING_REPORT→97`,
  `COMPLETED→100` (sólo cuando hay `analysisId`).
  Mientras `status≠SUCCESS`, **clamp ≤ 99** en backend o frontend (las dos
  defensas; mejor en ambas).
- Persistencia de métricas: actualizar el row del job con `update analysis_job
  set phase=?, progress=?, metrics_json=? where id=?` en cada transición. Para
  el avance fino dentro de `COUNTING_TOKENS` (potencialmente miles de
  archivos), se usa un throttle: actualizar máximo cada N archivos o cada
  M ms para no saturar la BD.

### Modelado de errores

- `RepositoryIntakeException` (URL inválida, repo grande, clone timeout) capturado
  dentro de `executeJob` → marcar job `FAILED` con `errorCode = exception.errorCode().name()`
  y `errorMessage = exception.getMessage()`. El polling devuelve HTTP 200 con
  `status=FAILED`. El frontend reutiliza `toUserMessage` con el `code`.
- Excepciones no esperadas → `FAILED` con `errorCode=ANALYSIS_FAILED`.
- `RATE_LIMITED` se mantiene como **HTTP 429** en `POST /api/analyze` (sin
  crear job), no se materializa como job fallido — comportamiento consistente
  con el actual.
- `INVALID_URL` se evalúa **antes** de crear el job (en el controller o en
  `submit`), por lo que sigue devolviendo HTTP 400 sin job creado.

### Fast-path (análisis reutilizable)

El ticket menciona "si no hay análisis reutilizable, devuelve jobId". Hoy NO
existe ningún concepto de cache de análisis. Dos opciones:

- (a) Implementar el cache ahora: `POST /api/analyze` busca último análisis
  para `repository_url`, y si tiene < X horas → devuelve 200 con
  `analysisId` directamente (sin `jobId`).
- (b) Diferir: por ahora siempre devolver `jobId`; el "fast-path" se queda
  como ticket aparte.
- **Recomendación**: (b). El alcance del ticket habla del observable async;
  el cache merece su propia decisión (TTL, invalidación al cambiar
  pricing/multipliers, semánticas de re-análisis manual). Modelar la
  respuesta como `JobSubmissionResponse { jobId, status, statusUrl,
  analysisId? }` deja el hueco abierto.

### Polling vs. SSE/WebSocket

- **Polling cada 1-2s** (el ticket): simple, infra ya soporta. Stateless en el
  backend.
- **SSE (Server-Sent Events)**: una sola conexión HTTP por cliente, push real.
  Spring soporta `SseEmitter`. Útil para repos grandes con muchos archivos.
- **WebSocket**: overkill para flujo de eventos uni-direccional.
- **Recomendación**: polling para v1 (criterio del ticket). Dejar la puerta
  abierta a SSE en futuro: el endpoint `GET /api/analyze/jobs/{id}` puede
  convivir con un `GET /api/analyze/jobs/{id}/events` SSE posterior.

### Retención y limpieza

- Tabla `analysis_job` crece sin límite si no se reapa.
- Opciones:
  - (a) Borrar jobs `SUCCESS`/`FAILED` con `finished_at < now() - 7d` mediante
    `@Scheduled` (similar al `RepositoryWorkspaceSweeper` existente).
  - (b) Mantenerlos para auditoría/leaderboard.
  - (c) Sólo eliminar `SUCCESS` antiguos (porque `analysis_id` ya contiene el
    resultado); preservar `FAILED` para diagnóstico.
- **Recomendación**: (c) con TTL configurable (`tokenmeter.analyze-throttle.job-retention`,
  default 7 días para SUCCESS, 30 días para FAILED). Definir en spec.

### Reaper / heartbeats

Un proceso que muere mid-job deja un job en `RUNNING` para siempre.

- **Recomendación mínima**: al arrancar la app, un `CommandLineRunner` marca
  todos los `RUNNING` con `started_at < now()-2h` como `FAILED` con
  `errorCode=ANALYSIS_INTERRUPTED`. Para v1 es suficiente. Heartbeats reales
  (campo `last_heartbeat_at` actualizado periódicamente + reaper `@Scheduled`)
  se pueden añadir si surgen incidencias.

## Recommendation

Resumen del approach recomendado:

1. **Modelo**: nueva tabla `analysis_job` (Flyway V6), entidad y repositorio
   JPA. Enums `JobStatus`, `JobPhase` en `domain/analyzer/`.
2. **Ejecución**: `@EnableAsync` + bean `analysisJobExecutor` (`ThreadPoolTaskExecutor`)
   con core/max = `tokenmeter.analyze-throttle.max-concurrent`, `queue-capacity = 0`,
   `AbortPolicy`.
3. **Contrato HTTP**:
   - `POST /api/analyze` → 202 Accepted, body
     `{jobId, status: QUEUED|RUNNING, statusUrl: "/api/analyze/jobs/{jobId}"}`.
     Errores síncronos previos al job (URL inválida → 400, rate-limit
     → 429) se mantienen.
   - `GET /api/analyze/jobs/{jobId}` → 200 con `AnalysisJobResponse`
     (incluye `status`, `phase`, `progressPercent` máximo 99 hasta SUCCESS,
     `metrics` parciales, `analysisId` cuando SUCCESS, `error` cuando FAILED).
     404 si no existe.
   - `GET /api/analyze/{id}` se mantiene tal cual.
4. **Pipeline**: `submit()` (rápido, persiste `QUEUED`, programa
   `executor.execute`) + `executeJob(jobId)` (todo el pipeline actual,
   actualizando fase/métricas/heartbeat en cada paso).
5. **Frontend**: hook `useAnalysisJob` con polling 1.5s + AbortController;
   `LoadingState` consume el job real; navegación a `/analysis/{id}` al SUCCESS.
6. **Tests**: backend tests para creación/consulta/transiciones; frontend
   añade vitest si conviene (proposal decidirá).
7. **Documentación**: actualizar `docs/API.md`, `README.md`, `CLAUDE.md`.

Mantener todo dentro del proyecto sin nuevas dependencias mayores (no broker,
no Modulith). El cambio cabe en: 1 migración Flyway, 1 entidad + 1 repo JPA,
1 nueva clase application (`AnalysisJobService` o método dentro de
`RepositoryAnalysisService`), 1 executor bean, 2 endpoints, 1 hook React,
ajustes en `LoadingState`.

## Risks

- **Hilos colgados en Tomcat**: si por error `executeJob` corre dentro del
  hilo HTTP (sin `@Async` o con el wiring mal), las llamadas largas siguen
  bloqueando el servlet container. Mitigación: test que verifique que el
  controller devuelve en <100ms incluso si el repo es grande (mockear
  cloner).
- **Transaccionalidad parcial**: las actualizaciones de progreso requieren
  commits intermedios para que el GET poll vea cambios. Si todo se envuelve
  en una sola transacción JPA larga, el poller no verá actualizaciones.
  Mitigación: cada `updateJob(...)` debe ser `@Transactional(propagation =
  REQUIRES_NEW)` o usar `JdbcTemplate` directo.
- **Race conditions en doble submit**: dos `POST` casi simultáneos para el
  mismo URL crearán dos jobs. Para v1 es aceptable (paridad con el
  comportamiento actual: dos análisis simultáneos crean dos rows). Diferir
  idempotencia/dedup al fast-path.
- **Memory leak en cola del executor**: con `queue-capacity = 0` (recomendado)
  no hay leak. Si se elige una cola, monitorizar tamaño en Prometheus.
- **Jobs huérfanos tras restart**: el reaper de arranque (`CommandLineRunner`)
  resuelve esto a coste despreciable.
- **Limpieza de temp dir**: hoy el `finally` borra `cloneDirectory`. Tras el
  refactor el `finally` debe seguir vivo (no se puede mover a un
  `@PreDestroy`). Riesgo de fuga de disco si el executor mata el thread por
  shutdown brusco. Mitigación: graceful shutdown del executor +
  `RepositoryWorkspaceSweeper` (ya existente) como red de seguridad.
- **Frontend ciclo de vida**: si el usuario cierra la pestaña a mitad de
  polling y vuelve con la misma URL via shared link, no debería refetchear
  el job (ya está en `/analysis/{id}`). Esto ya funciona, sólo verificar.
- **Clamp a 99%**: si el frontend o backend olvida el clamp, el usuario
  puede ver 100% antes de tener `analysisId`. Defender en ambos lados.
- **Compatibilidad de pruebas existentes**: `RepositoryAnalysisServiceTest`
  hoy asume retorno síncrono. Hay que migrar los tests para invocar
  `executeJob` directamente (o el método de pipeline interno) y aserciones
  sobre `AnalysisJobRepository`.

## Open Questions

1. **Concurrencia**: ¿core-size del executor = `max-concurrent` actual? ¿O
   exponemos una propiedad nueva `tokenmeter.jobs.max-concurrent`? Recomendado
   reutilizar `max-concurrent` para no romper config existente.
2. **Cola del executor**: `queue-capacity = 0` (preserva 429) vs > 0 (acepta
   QUEUED). El ticket asume el estado `QUEUED` existe; sugiere que SÍ debe
   haber cola. Decidir en proposal.
3. **Tiempo de retención**: ¿7d SUCCESS, 30d FAILED por defecto? ¿O
   conservamos todo y delegamos a un cron manual?
4. **Tests frontend**: ¿añadimos vitest + @testing-library/react para el
   hook de polling? ¿O nos quedamos con tests manuales/smoke?
5. **Fast-path**: ¿incluido en este ticket o sale como TKM-44? Recomendado
   sale como ticket separado.
6. **Granularidad de progreso en `COUNTING_TOKENS`**: ¿actualizamos cada N
   archivos? ¿Cada M ms? ¿Sólo al final de la fase? Decidir umbral en
   spec/design.
7. **Idempotencia / dedup**: ¿`Idempotency-Key` HTTP header? Diferir a otro
   ticket — fuera de alcance.
8. **`POST /api/analyze` status code**: hoy es **200 OK** con resultado
   inline. Tras el cambio será **202 Accepted** con `jobId`. ¿Hay clientes
   externos que dependan del 200? Buscar en frontend (sólo
   `services/api.ts`) y badge consumers — el badge SVG usa
   `GET /api/analyze/{id}/badge.svg`, no `POST`, así que no afecta. OK.

## Ready for Proposal

**Sí.** El siguiente paso es `sdd-propose` con el approach recomendado y las
preguntas abiertas anotadas (idealmente respondidas por el equipo antes de
escribir el spec).
