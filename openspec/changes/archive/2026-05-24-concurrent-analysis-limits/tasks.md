# Tasks — concurrent-analysis-limits

Total: 41 items.

## 1. Dominio

- [x] 1.1 Crear `AnalysisJobQueueState` como record con `(int runningCount, int maxConcurrency, Integer queuePosition)` y validaciones (`runningCount >= 0`, `maxConcurrency >= 1`, `queuePosition == null || queuePosition >= 1`) en `backend/src/main/java/dev/diegobarrioh/tokenmeter/domain/job/AnalysisJobQueueState.java`.
- [x] 1.2 Crear `AnalysisJobView` como record con `(AnalysisJobSnapshot snapshot, AnalysisJobQueueState queueState)` (queueState nullable) en `backend/src/main/java/dev/diegobarrioh/tokenmeter/domain/job/AnalysisJobView.java`.
- [x] 1.3 Añadir test unitario `AnalysisJobQueueStateTest` (invariantes: rechazo de runningCount negativo, maxConcurrency <= 0, queuePosition == 0, aceptación de queuePosition null) en `backend/src/test/java/dev/diegobarrioh/tokenmeter/domain/job/AnalysisJobQueueStateTest.java`.
- [x] 1.4 Añadir test unitario `AnalysisJobViewTest` (snapshot obligatorio, queueState opcional, igualdad por valor) en `backend/src/test/java/dev/diegobarrioh/tokenmeter/domain/job/AnalysisJobViewTest.java`.

## 2. Persistencia

- [x] 2.1 Crear migración `backend/src/main/resources/db/migration/V7__analysis_job_queue_index.sql` con `CREATE INDEX IF NOT EXISTS idx_analysis_job_status_created_at ON analysis_job (status, created_at);` y comentario explicando ambos casos de uso (countByStatus + countQueuedAheadOf).
- [x] 2.2 Extender el port `AnalysisJobRepository` en `backend/src/main/java/dev/diegobarrioh/tokenmeter/application/analyzer/AnalysisJobRepository.java` con `int countByStatus(AnalysisJobStatus status)` y `int countQueuedAheadOf(AnalysisJobId targetId)` (incluir javadoc: posición FIFO 1-based, devuelve 0 si el job ya no es QUEUED).
- [x] 2.3 Añadir en `AnalysisJobJpaRepository` (`backend/src/main/java/dev/diegobarrioh/tokenmeter/infrastructure/persistence/analysis/jobs/AnalysisJobJpaRepository.java`) el método derivado `int countByStatus(AnalysisJobStatus status)` y la query JPQL `@Query("select count(j) from AnalysisJobEntity j where j.status = 'QUEUED' and (j.createdAt < :createdAt or (j.createdAt = :createdAt and j.id < :id))") int countQueuedAheadOf(@Param("createdAt") Instant createdAt, @Param("id") UUID id);`.
- [x] 2.4 Implementar en `JpaAnalysisJobRepository` (`backend/src/main/java/dev/diegobarrioh/tokenmeter/infrastructure/persistence/analysis/jobs/JpaAnalysisJobRepository.java`) los nuevos métodos del port: `countByStatus` delega directo; `countQueuedAheadOf` hace `findById`, devuelve 0 si la entidad no existe o su status != QUEUED, en caso contrario delega y suma 1.
- [x] 2.5 Añadir test `@DataJpaTest` en `backend/src/test/java/dev/diegobarrioh/tokenmeter/infrastructure/persistence/analysis/jobs/JpaAnalysisJobRepositoryTest.java` cubriendo: `countByStatus_returnsExpectedCountsPerStatus`, `countQueuedAheadOf_returnsFifoPositionUsingCreatedAt` (siembra 4 QUEUED con `createdAt` distintos → posiciones 1..4), `countQueuedAheadOf_breaksTiesById` (mismo `createdAt`, distinto UUID), `countQueuedAheadOf_returnsZeroWhenJobIsNotQueued`.

## 3. Aplicación

- [x] 3.1 Subir el default de `queueCapacity` de 32 a 256 en `AnalyzeThrottleProperties` (`backend/src/main/java/dev/diegobarrioh/tokenmeter/application/analyzer/AnalyzeThrottleProperties.java`) manteniendo la validación `>= 1`.
- [x] 3.2 Actualizar `tokenmeter.analyze-throttle.queue-capacity: 256` en `backend/src/main/resources/application.yml` (y resto de perfiles `application-*.yml` que ya declaren la clave) para reflejar el nuevo default explícitamente.
- [x] 3.3 En `AnalysisJobSubmissionService` (`backend/src/main/java/dev/diegobarrioh/tokenmeter/application/analyzer/AnalysisJobSubmissionService.java`) actualizar el mensaje de la `RepositoryIntakeException` a `"Analysis queue is full"`, el log al `warn` actual a `"queue ceiling reached for jobId={}"` y el javadoc/comentario de la rama `RejectedExecutionException` para aclarar que dispara solo cuando el `LinkedBlockingQueue` alcanza `queueCapacity` (no por contención de slot). Sin cambios estructurales.
- [x] 3.4 Inyectar `AnalyzeThrottleProperties` en `AnalysisJobQueryService` (`backend/src/main/java/dev/diegobarrioh/tokenmeter/application/analyzer/AnalysisJobQueryService.java`) y añadir `Optional<AnalysisJobView> getView(AnalysisJobId id)`: resuelve snapshot vía repositorio; si status ∈ {SUCCESS, FAILED} → `AnalysisJobView(snapshot, null)`; si RUNNING → queueState con `runningCount = countByStatus(RUNNING)`, `maxConcurrency = props.maxConcurrent()`, `queuePosition = null`; si QUEUED → además `queuePosition = countQueuedAheadOf(id)`. Mantener `findById`/`getSnapshot` intactos.
- [x] 3.5 Añadir tests en `backend/src/test/java/dev/diegobarrioh/tokenmeter/application/analyzer/AnalysisJobQueryServiceTest.java`: `getView_includesQueueStateForQueuedJob`, `getView_omitsQueuePositionForRunningJob`, `getView_omitsQueueStateForTerminalJob` (uno por status SUCCESS y otro por FAILED en el mismo método o dos hermanos). Stubs sobre el port `AnalysisJobRepository` + `AnalyzeThrottleProperties` real.

## 4. Capa web

- [x] 4.1 Añadir record interno `QueueStateResponse(int runningCount, int maxConcurrency, Integer queuePosition)` y campo `QueueStateResponse queueState` (nullable, último parámetro) en `AnalysisJobStatusResponse` (`backend/src/main/java/dev/diegobarrioh/tokenmeter/infrastructure/web/analyzer/AnalysisJobStatusResponse.java`), respetando el orden de records anidados existente.
- [x] 4.2 Cambiar la firma de `AnalysisJobResponseMapper.toStatus(...)` (`backend/src/main/java/dev/diegobarrioh/tokenmeter/infrastructure/web/analyzer/AnalysisJobResponseMapper.java`) a aceptar `AnalysisJobView`: mapea `queueState` solo cuando `view.queueState() != null`; calcula `phaseLabel = "Waiting for an analysis slot"` cuando `snapshot.status() == QUEUED && qs != null && qs.runningCount() >= qs.maxConcurrency()`; en otro caso conserva el label actual. Comentar en el método el branch del race `runningCount < maxConcurrency` durante la ventana previa a `markStarted`.
- [x] 4.3 Actualizar `AnalysisJobController.getJob` (`backend/src/main/java/dev/diegobarrioh/tokenmeter/infrastructure/web/analyzer/AnalysisJobController.java`) para llamar a `queryService.getView(...)` y pasar el `AnalysisJobView` al mapper. `submit` queda intacto.
- [x] 4.4 Refrescar javadoc de `AsyncExecutionConfig` (`backend/src/main/java/dev/diegobarrioh/tokenmeter/infrastructure/config/AsyncExecutionConfig.java`) explicando que `AbortPolicy` protege ahora el techo de cola (no la concurrencia de slots). Sin cambios funcionales.
- [x] 4.5 Añadir test `AnalysisJobControllerTest.getJob_includesQueueStateWhenQueued` (asserta `queueState.runningCount`, `queueState.maxConcurrency`, `queueState.queuePosition >= 1`) en `backend/src/test/java/dev/diegobarrioh/tokenmeter/infrastructure/web/analyzer/AnalysisJobControllerTest.java`.
- [x] 4.6 Añadir test `AnalysisJobControllerTest.getJob_phaseLabelWaitingWhenSlotContention` (mock view con QUEUED y runningCount == maxConcurrency → `phaseLabel == "Waiting for an analysis slot"`).
- [x] 4.7 Añadir test `AnalysisJobControllerTest.getJob_omitsQueueStateForTerminalJob` (status SUCCESS → `queueState` ausente o null en JSON).
- [x] 4.8 Renombrar `AnalysisJobControllerTest.postWhenExecutorSaturatedReturns429RateLimited` → `postWhenQueueCeilingReachedReturns429`, configurar `tokenmeter.analyze-throttle.queue-capacity=1` y `max-concurrent=1`, bloquear el worker con un `CountDownLatch`, lanzar 1 submission que satura slot (202 QUEUED), 1 que satura queue (202 QUEUED), y la tercera que dispara `AbortPolicy` → `429 RATE_LIMITED` con `error.message` mencionando "queue".

## 5. Executor y propiedades por defecto

- [x] 5.1 Verificar (sin cambios de código) que `AsyncExecutionConfig.analysisJobExecutor(...)` lee `props.queueCapacity()` para dimensionar el `LinkedBlockingQueue`; documentar en commit que el bump a 256 es transparente.
- [x] 5.2 Añadir o extender test en `backend/src/test/java/dev/diegobarrioh/tokenmeter/infrastructure/config/AsyncExecutionConfigTest.java` que asserta `analysisJobExecutor` con la propiedad por defecto expone `queueCapacity == 256` (vía `ThreadPoolTaskExecutor.getQueueCapacity()`).

## 6. Migración de tests de submission

- [x] 6.1 Renombrar `AnalysisJobSubmissionServiceTest.rejectsWith429AndRollsBackRowWhenExecutorIsSaturated` → `rejectsWith429WhenQueueCeilingReached` en `backend/src/test/java/dev/diegobarrioh/tokenmeter/application/analyzer/AnalysisJobSubmissionServiceTest.java`. Actualizar wording (mensajes, comentarios) para reflejar "queue ceiling", mantener la assertion de borrado de la fila `analysis_job` y que el mensaje de error mencione la cola llena.

## 7. Tests de integración de concurrencia

- [x] 7.1 Si `org.awaitility:awaitility` no figura en `backend/build.gradle.kts`, añadirlo en bloque `testImplementation` con la versión alineada al BOM de Spring Boot 3.5. Anotar en el commit.
- [x] 7.2 Crear `backend/src/test/java/dev/diegobarrioh/tokenmeter/application/analyzer/AnalysisJobExecutionServiceConcurrencyIT.java` con `@SpringBootTest` (perfil test, H2): test `runningCountNeverExceedsMaxConcurrency` configura `max-concurrent=2`, somete 5 jobs con `CountDownLatch` que retiene el worker, usa Awaitility para esperar `runningCount == 2` y asserta que durante toda la corrida `repository.countByStatus(RUNNING) <= 2`.
- [x] 7.3 En el mismo IT añadir `failedJobReleasesSlotForNextQueued`: somete `max-concurrent + 1` jobs, forza fallo del primero (stub o repo con URL inválida ya en RUNNING), Awaitility espera que el `QUEUED` cabecero transicione a `RUNNING` y verifica que `runningCount` permanece `<= maxConcurrency`.

## 8. Tipos y UI frontend

- [x] 8.1 Extender `frontend/src/types/api.ts`: exportar `interface AnalysisJobQueueStateResponse { runningCount: number; maxConcurrency: number; queuePosition?: number | null }` y añadir `queueState?: AnalysisJobQueueStateResponse | null` a `AnalysisJobStatusResponse`.
- [x] 8.2 En `frontend/src/pages/DashboardPage.tsx` (`LoadingState`): cuando `job?.status === 'QUEUED' && job.queueState`, renderizar bajo el `phaseLabel` un `<p className="...">` con texto tipo `Position {queuePosition ?? '?'} · {runningCount}/{maxConcurrency} running` reutilizando las clases Tailwind ya presentes para subtítulos del estado. Sin nuevo componente helper.

## 9. Tests frontend

- [x] 9.1 Añadir un escenario en `frontend/src/hooks/useAnalysisJob.test.ts` (o ruta equivalente del test ya existente) que mockea la respuesta `GET /api/analyze/jobs/{id}` con `status='QUEUED'` y `queueState` poblado, y asserta que el hook lo expone tal cual al consumidor.

## 10. Documentación

- [x] 10.1 Actualizar `docs/API.md`: documentar el objeto `queueState` (campos, nullabilidad por status), redefinir las dos causas de `429 RATE_LIMITED` (rate limiter por IP + techo de cola), señalar que la saturación de slots ya NO devuelve 429, y consignar `queueCapacity` default = 256.
- [x] 10.2 Añadir subsección "Concurrency cap" dentro de la sección de jobs de análisis en `docs/ARCHITECTURE.md` explicando la cola FIFO interna del executor, los nuevos campos `countByStatus`/`countQueuedAheadOf` y el cómputo on-read de `queueState`.
- [x] 10.3 Actualizar `CLAUDE.md` con una línea referenciando el campo `queueState` y los nuevos defaults (`queueCapacity=256`, semántica de `429`).

## 11. Smoke manual

- [x] 11.1 Con `tokenmeter.analyze-throttle.max-concurrent=1` en `application-local.yml`, lanzar `maxConcurrent + 2` (3) `curl -X POST /api/analyze` consecutivos contra repos pequeños, anotar los `jobId`, pollear cada uno con `GET /api/analyze/jobs/{jobId}` cada ~1.5 s y verificar que el 2º muestra `queuePosition=1`, el 3º `queuePosition=2`, ambos con `phaseLabel="Waiting for an analysis slot"`; al completarse el 1º el `queuePosition` del 3º debe bajar a 1 y el 2º transicionar a `RUNNING`.

  **Checklist concreto** (preparar `application-local.yml` con `tokenmeter.analyze-throttle.max-concurrent: 1`, arrancar el backend en `:8080`):

  ```bash
  # 1) Tres submissions consecutivas → todas devuelven 202.
  J1=$(curl -s -X POST http://localhost:8080/api/analyze \
        -H 'Content-Type: application/json' \
        -d '{"repositoryUrl":"https://github.com/octocat/Hello-World"}' | jq -r .jobId)
  J2=$(curl -s -X POST http://localhost:8080/api/analyze \
        -H 'Content-Type: application/json' \
        -d '{"repositoryUrl":"https://github.com/octocat/Spoon-Knife"}' | jq -r .jobId)
  J3=$(curl -s -X POST http://localhost:8080/api/analyze \
        -H 'Content-Type: application/json' \
        -d '{"repositoryUrl":"https://github.com/octocat/git-consortium"}' | jq -r .jobId)
  echo "$J1 $J2 $J3"

  # 2) Polling cada ~1.5s, en otro terminal o en bucle:
  for JOB in "$J1" "$J2" "$J3"; do
    echo "== $JOB =="
    curl -s http://localhost:8080/api/analyze/jobs/"$JOB" \
      | jq '{status, phaseLabel, queueState}'
  done
  # Esperar 1.5 s y repetir.
  ```

  **Resultado esperado** en el primer ciclo de poll:

  - `J1`: `status=RUNNING`, `queueState.runningCount=1`, `queueState.maxConcurrency=1`, `queueState.queuePosition=null`.
  - `J2`: `status=QUEUED`, `phaseLabel="Waiting for an analysis slot"`, `queueState.queuePosition=1`.
  - `J3`: `status=QUEUED`, `phaseLabel="Waiting for an analysis slot"`, `queueState.queuePosition=2`.

  Tras la finalización de `J1` (`status=SUCCESS`, `queueState=null`), un poll posterior debe mostrar `J2` ya en `RUNNING` y `J3` con `queuePosition=1`. Cuando `J2` finaliza, `J3` pasa a `RUNNING`.
