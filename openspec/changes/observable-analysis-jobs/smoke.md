# Smoke test manual — observable-analysis-jobs

Plan de verificación manual end-to-end del cambio. Ejecutar desde la raíz del repo. Cada paso lista la acción y el observable esperado. Documentar el resultado en el PR (capturas opcionales).

> Pre-requisitos: `.env` configurado (`TOKENMETER_DB_USER`, `TOKENMETER_DB_PASSWORD`), Docker + Docker Compose, `git`, `curl`, `jq` (opcional para formatear JSON).

## 1. Arranque del stack

**Acción**:
```bash
docker compose up --build -d
docker compose ps
```

**Esperado**:
- `db`, `backend` y `frontend` en estado `Up (healthy)`.
- `curl -s http://localhost:8081/actuator/health` devuelve `{"status":"UP"}`.
- Logs de arranque incluyen `AnalysisJobReaper` (warning con cuenta `0` si la BD está limpia) y el bean `analysisJobExecutor`.

---

## 2. Happy path con repo pequeño

**Acción**:
```bash
curl -s -X POST http://localhost:8081/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"repositoryUrl":"https://github.com/guilu/tokenmeter"}'
```

**Esperado**:
- `HTTP/1.1 202 Accepted`.
- Body con `jobId` (UUID), `status:"QUEUED"`, `statusUrl:"/api/analyze/jobs/<jobId>"`, `analysisId:null`.

**Acción** (polling):
```bash
JOB=<jobId>
watch -n 1 "curl -s http://localhost:8081/api/analyze/jobs/$JOB | jq '{status,phase,progressPercent,analysisId}'"
```

**Esperado**:
- Transición observable `QUEUED → RUNNING` (`phase` avanza forward-only: `CLONING_REPOSITORY → SCANNING_FILES → COUNTING_TOKENS → CALCULATING_COSTS → SAVING_REPORT → COMPLETED`).
- `progressPercent` crece y **se queda en ≤ 99 hasta el snapshot final**.
- Snapshot terminal: `status="SUCCESS"`, `phase="COMPLETED"`, `progressPercent=100`, `analysisId` no nulo.

**Acción** (vista del resultado):
- Abrir `http://localhost:3001`, lanzar el mismo repo desde la UI y verificar que tras el `LoadingState` aparece la vista de resultados en la URL `analysis/<analysisId>`.

---

## 3. Repo grande con fases visibles

**Acción**: lanzar desde la UI un repo más pesado (> 30 s de procesamiento), por ejemplo `https://github.com/spring-projects/spring-boot`.

**Esperado**:
- El `LoadingState` muestra los 8 stages.
- Las fases `CALCULATING_COSTS` y `SAVING_REPORT` son **visibles** durante varios segundos (no se saltan instantáneamente).
- La barra de progreso **nunca alcanza 100 %** hasta el snapshot final con `status=SUCCESS`.
- Tras `SUCCESS`, el polling se detiene (DevTools → Network: deja de haber peticiones a `/api/analyze/jobs/...`).

---

## 4. URL inválida → 400 sin job creado

**Acción**:
```bash
curl -s -X POST http://localhost:8081/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"repositoryUrl":"not-a-valid-url"}'
```

Y la variante desde la UI introduciendo cualquier string que no sea de github.

**Esperado**:
- `HTTP/1.1 400 Bad Request` con body `{"code":"INVALID_URL", ...}`.
- En la UI: alerta de error inmediata; no se crea ningún job (verificar en la BD `SELECT count(*) FROM analysis_job` queda invariante).

---

## 5. Fallo de clone → FAILED con error visible

**Acción**: lanzar un análisis sobre un repo privado o inexistente, p.ej. `https://github.com/anthropics/this-repo-does-not-exist-xyz`.

```bash
curl -s -X POST http://localhost:8081/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"repositoryUrl":"https://github.com/anthropics/this-repo-does-not-exist-xyz"}'
```

**Esperado**:
- El POST devuelve `202 Accepted` con `jobId`.
- El polling termina con `status="FAILED"`, `phase="FAILED"`, `error.code ∈ {ANALYSIS_FAILED, CLONE_TIMEOUT}`, `error.message` no vacío, `analysisId=null`, `completedAt` no nulo.
- En la UI: alerta de error renderizada vía `toUserMessage(...)`. `activeJobId` se limpia.

---

## 6. Rate limiting → 429 cuando executor y cola están saturados

**Acción**: con `tokenmeter.analyze-throttle.max-concurrent=3` y `queue-capacity=32`, lanzar 36 análisis en paralelo (3 workers + 32 cola + 1 que debe rebotar):

```bash
for i in $(seq 1 36); do
  curl -s -o /tmp/r$i -w "%{http_code}\n" -X POST http://localhost:8081/api/analyze \
    -H 'Content-Type: application/json' \
    -d '{"repositoryUrl":"https://github.com/spring-projects/spring-boot"}' &
done
wait
```

**Esperado**:
- 3 jobs en `RUNNING`, hasta 32 en `QUEUED`, y al menos uno responde `429` con `{"code":"RATE_LIMITED", ...}`.
- Las ráfagas cortas (≤ 35 jobs) deben encolarse sin devolver 429 — verifica el cambio respecto a la versión síncrona.

---

## 7. Reinicio mid-job → reaper marca JOB_INTERRUPTED

**Acción**: lanzar un análisis grande y, mientras `status="RUNNING"` (verificar con el polling), reiniciar el backend:
```bash
docker compose restart backend
```

Tras el restart, pollear de nuevo el `jobId`:
```bash
curl -s http://localhost:8081/api/analyze/jobs/$JOB | jq
```

**Esperado**:
- Logs del backend al boot incluyen `AnalysisJobReaper` con cuenta de jobs no terminales reconciliados.
- El job queda en `status="FAILED"`, `phase="FAILED"`, `error.code="JOB_INTERRUPTED"`, `error.message="Analysis interrupted by service restart"`, `completedAt` no nulo.
- En la UI (si seguía abierta): tras la primera respuesta tras el restart, se muestra el mensaje de error correspondiente.

---

## 8. Invariante "no 100 % antes de SUCCESS"

**Acción**: durante cualquiera de los pasos anteriores, capturar al menos 5 snapshots intermedios del polling.

**Esperado**:
- Para todo snapshot con `status ∈ {QUEUED, RUNNING}`: `progressPercent ≤ 99`.
- El único snapshot con `progressPercent = 100` tiene `status = "SUCCESS"` y `analysisId != null`.

---

## Reporte en el PR

Para cada bloque, anotar:

- ✅/❌ pasa.
- Si ❌: captura + logs relevantes (`docker compose logs backend | tail -100`).
- Tiempos aproximados de cada fase (útil para detectar regresiones futuras).
