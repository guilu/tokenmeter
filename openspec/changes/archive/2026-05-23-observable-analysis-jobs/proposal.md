# Proposal: Observable Analysis Jobs (TKM-43)

## Intent

Hoy `POST /api/analyze` corre toda la pipeline (clone → scan → tokenize → estimate → persist) de forma síncrona dentro del hilo HTTP, mientras el frontend simula progreso con un timer que alcanza 100 % aunque el backend siga trabajando. Resultado: análisis largos quedan reportados como "completados" antes de existir, no hay observabilidad real del avance y los fallos no dejan rastro. Convertimos cada análisis en un **job asíncrono observable**, persistido, con fases reales y un endpoint de consulta, manteniendo la UX actual del AI Analysis Pipeline.

## Scope

### In Scope
- Modelo `AnalysisJob` (estado, fase, métricas, errores, timestamps) en `domain/analyzer/` + entidad y repo JPA en `infrastructure/persistence/analysis/jobs/`.
- Nueva migración Flyway `V6__analysis_job.sql` (tabla `analysis_job`, índices por `status` y `created_at`). No se tocan V1–V5 ni la tabla `analysis`.
- `AnalysisJobExecutor` (`ThreadPoolTaskExecutor` dedicado) con `core/max = tokenmeter.analyze-throttle.max-concurrent` y `queue-capacity = 32` (config `tokenmeter.analyze-throttle.queue-capacity`, default 32).
- Refactor de `RepositoryAnalysisService` en `submit(url)` (rápido, persiste `QUEUED`, programa la ejecución) y `executeJob(jobId)` (pipeline actual instrumentado por fase/métrica).
- `POST /api/analyze` cambia a **202 Accepted** y devuelve `{ jobId, status, statusUrl, analysisId? }` (campo `analysisId` reservado para el fast-path futuro; en este ticket siempre `null`).
- Nuevo `GET /api/analyze/jobs/{jobId}` → 200 con `AnalysisJobResponse` (`status`, `phase`, `phaseLabel`, `progressPercent`, `message`, timestamps, `metrics`, `analysisId?`, `error{code,message}?`). 404 si no existe. **Fuera del rate-limit interceptor** (poll rápido).
- Errores síncronos previos al job intactos: `INVALID_URL` → 400, `RATE_LIMITED` → 429 (sin crear job). Errores asíncronos (clone timeout, repo too large, tokenizer, pricing) materializan `FAILED` con `errorCode`/`errorMessage`.
- Reaper de arranque (`CommandLineRunner`): jobs en `RUNNING` heredados de un proceso anterior → `FAILED` con `errorCode=JOB_INTERRUPTED`.
- Limpieza programada (`@Scheduled`): purga `SUCCESS` tras 7 d, `FAILED` tras 30 d. Ventanas configurables (`tokenmeter.analyze-throttle.retention.success`, `.failed`).
- Frontend: tipos nuevos (`JobSubmissionResponse`, `AnalysisJobResponse`, enums `JobStatus`/`JobPhase`); cliente `submitAnalysis` y `getAnalysisJob`; hook `useAnalysisJob` (polling 1.5 s, cancelable); `LoadingState` reconectado al job real (fase, métricas y progreso vienen del backend, clamp `≤ 99` hasta `SUCCESS + analysisId`).
- Setup `vitest` + `@testing-library/react` + jsdom y tests del hook (`SUCCESS`, `FAILED`, no-100-antes-de-SUCCESS).

### Out of Scope
- **Fast-path / reusable analysis** (cache por URL). La respuesta deja `analysisId?` reservado, pero este ticket siempre crea un job nuevo. Ticket separado.
- Header `Idempotency-Key` y deduplicación de submissions concurrentes.
- Transporte push (SSE / WebSocket) — sólo polling.
- Distribución multi-nodo del executor (cola DB-backed, lease/heartbeat continuo).
- Retries automáticos de jobs fallidos.

## Approach

1. **Persistencia primero**: migración V6 crea `analysis_job` con `status`, `phase`, `progress_percent`, `metrics_json`, `error_code`, `error_message`, `analysis_id` (FK opcional), timestamps. Indices `status` y `created_at`.
2. **Ejecutor dedicado**: `@EnableAsync` + `@Bean analysisJobExecutor` con cola acotada (32) y `AbortPolicy`. Cuando satura, `submit` devuelve 429 (paridad con el rate-limit actual). Las actualizaciones de progreso se hacen en transacciones cortas (`REQUIRES_NEW`) para que el poll vea cambios.
3. **Pipeline instrumentada**: `executeJob` envuelve los pasos existentes (`cloner`, `sizeCalculator`, `scanner`, `tokenizer`, `estimator`, `persistence`) actualizando fase y métricas en cada transición. Granularidad fina sólo dentro de `COUNTING_TOKENS`: emitir progreso al entrar/salir de la fase y, durante el bucle, como máximo cada ~250 ms (guideline, no contrato duro). `progressPercent` se clampa a 99 hasta `SUCCESS + analysisId`.
4. **Contrato HTTP**: el controller `submit` ejecuta validaciones síncronas (`GitHubRepositoryUrl.parse`, semáforo / cola del executor) y devuelve `202` en < 100 ms. `GET /api/analyze/jobs/{id}` siempre 200 (o 404), nunca 5xx por fallos del job; los fallos viven dentro del body como `status=FAILED`.
5. **Frontend rewiring**: `useAnalysisJob` reemplaza el `setInterval` de `LoadingState`. Mapping `JobPhase → stage` mantiene las 8 etapas visuales actuales. Al `SUCCESS + analysisId`, navegar a `/analysis/{id}` y dejar al flujo existente cargar el resultado. Al `FAILED`, mostrar `error.message` mediante `toUserMessage(code)`.
6. **Resiliencia**: reaper en `CommandLineRunner` + `@Scheduled` de retención + `RepositoryWorkspaceSweeper` (existente) como red de seguridad para el temp dir.

## Affected Areas

| Área | Impacto | Descripción |
|------|---------|-------------|
| `backend/src/main/resources/db/migration/V6__analysis_job.sql` | New | Tabla `analysis_job` + índices. |
| `domain/analyzer/AnalysisJob*` (`JobStatus`, `JobPhase`, record) | New | Modelo de dominio del job. |
| `application/analyzer/RepositoryAnalysisService` | Modified | Split en `submit(url)` + `executeJob(jobId)`. |
| `application/analyzer/AnalysisJobRepository` (port) | New | Interfaz application → infra. |
| `application/analyzer/AnalysisJobReaper`, `AnalysisJobRetentionScheduler` | New | Reaper de arranque + cleanup programado. |
| `infrastructure/persistence/analysis/jobs/*` | New | Entidad JPA, repo Spring Data, adapter. |
| `infrastructure/web/analyzer/RepositoryAnalysisController` | Modified | `POST /api/analyze` 202 + nuevo `GET /api/analyze/jobs/{id}`. |
| `infrastructure/web/analyzer/*Response`, `*Mapper`, `WebMvcConfiguration` | Modified | DTOs nuevos; interceptor de rate-limit sólo en POST. |
| `infrastructure/scheduling/` (o `application/analyzer/`) | New | Wiring de `@EnableAsync` y bean `analysisJobExecutor`. |
| `backend/src/main/resources/application.yml` | Modified | Nuevas props (`queue-capacity`, `retention.success`, `retention.failed`). |
| `frontend/src/types/api.ts`, `services/api.ts` | Modified | Nuevos tipos y endpoints cliente. |
| `frontend/src/hooks/useAnalysisJob.ts` | New | Hook de polling. |
| `frontend/src/pages/DashboardPage.tsx` (`LoadingState`) | Modified | Reconectado al job real. |
| `frontend/package.json`, `vitest.config.ts`, `frontend/src/test/setup.ts` | New | Setup vitest + RTL + jsdom. |
| `docs/API.md`, `README.md`, `CLAUDE.md` | Modified | Documentar contrato async. |

## Risks

| Riesgo | Likelihood | Mitigación |
|--------|------------|------------|
| Pipeline sigue corriendo en el hilo HTTP si el wiring `@Async` falla | Med | Test de controller que mockea el cloner con sleep y asegura respuesta < 100 ms. |
| Poll no ve avance por transacción JPA larga | Med | Actualizaciones de progreso en `@Transactional(REQUIRES_NEW)` (o `JdbcTemplate`). |
| Jobs huérfanos tras crash/restart | Low | Reaper en `CommandLineRunner` marca `RUNNING` viejos como `FAILED/JOB_INTERRUPTED`. |
| Frontend muestra 100 % antes de `analysisId` | Med | Clamp `≤ 99` en backend Y frontend hasta `SUCCESS + analysisId`. Test del hook cubre la regla. |
| Crecimiento ilimitado de `analysis_job` | Low | `@Scheduled` de retención (7 d SUCCESS / 30 d FAILED) configurable. |
| Tests existentes (`RepositoryAnalysisServiceTest`) asumen retorno síncrono | High | Migrar tests para invocar `executeJob` directamente y aserciones sobre `AnalysisJobRepository`. |

## Rollback Plan

1. Revertir el commit que activa `@EnableAsync` y registra `analysisJobExecutor`.
2. Restaurar `POST /api/analyze` al comportamiento síncrono (`RepositoryAnalysisService.analyze`) y el contrato 200 OK.
3. Retirar `GET /api/analyze/jobs/{id}` del controller y del cliente.
4. Restaurar `LoadingState` con su timer (commit anterior).
5. **No** revertir la migración V6 en producción si ya hay datos; en su lugar, crear `V7__drop_analysis_job.sql` para eliminar la tabla cuando esté confirmado el rollback. Localmente, `flyway clean` o regenerar la BBDD.
6. Volver `package.json` al estado previo (quitar `vitest`/`@testing-library/react`/`jsdom`) si se desea.

## Dependencies

- Ninguna externa nueva en backend (`ThreadPoolTaskExecutor` ya viene con Spring).
- Frontend: `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `jsdom` (dev-dependencies). Justificado por la necesidad de tests del hook de polling.

## Open Questions / Non-decisions

- **Fast-path / reutilización de análisis**: deferido a ticket separado. El contrato deja `analysisId?` en la respuesta para no romper clientes cuando se añada.
- **`Idempotency-Key`**: fuera de alcance.
- **SSE / WebSocket**: descartado para v1; el endpoint actual puede coexistir con un `/events` SSE posterior sin romper polling.

## Success Criteria

- [ ] `POST /api/analyze` responde **202** en < 100 ms con `{ jobId, status, statusUrl, analysisId? }` (test con cloner mockeado lento).
- [ ] Errores síncronos siguen siendo 400 (`INVALID_URL`) y 429 (`RATE_LIMITED`) **sin** crear job.
- [ ] `GET /api/analyze/jobs/{id}` responde con la transición real `QUEUED → RUNNING(phase…) → SUCCESS|FAILED`; 404 si el id no existe.
- [ ] `progressPercent` nunca llega a 100 hasta que `status = SUCCESS` y `analysisId != null` (verificado en tests backend y frontend).
- [ ] Job `FAILED` propaga `errorCode`/`errorMessage` y la UI los muestra vía `toUserMessage`.
- [ ] Migración V6 aplicada limpiamente sobre un esquema con V1–V5 (test de migración o `./gradlew check` verde).
- [ ] Reaper transiciona jobs `RUNNING` huérfanos a `FAILED/JOB_INTERRUPTED` al arrancar.
- [ ] `@Scheduled` de retención purga `SUCCESS > 7 d` y `FAILED > 30 d` (cobertura por test con clock simulado).
- [ ] Setup `vitest` + RTL operativo: `npm run test` ejecuta como mínimo los tests del hook (`SUCCESS`, `FAILED`, no-100-antes-de-SUCCESS).
- [ ] `./gradlew check` y `(cd frontend && npm run build && npm run lint && npm run test)` verdes.
- [ ] `docs/API.md`, `README.md` y `CLAUDE.md` reflejan el nuevo contrato.
