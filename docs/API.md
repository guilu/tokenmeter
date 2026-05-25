# API Reference

Base URL en desarrollo: `http://localhost:8080`. En producción: `https://tokenmeter.backendtothefuture.com/api`.

Todas las respuestas son `application/json; charset=utf-8`.

## Autenticación

No requerida. La API es pública (a futuro: API key + rate limiting).

## Esquema de error

Cualquier endpoint puede devolver:

```json
{
  "code": "INVALID_URL",
  "message": "Repository URL is required",
  "status": 400,
  "path": "/api/analyze",
  "timestamp": "2026-05-09T10:15:30.123Z"
}
```

| `code` | HTTP | Cuándo |
|---|---|---|
| `INVALID_URL` | 400 | URL ausente, malformada o no de github.com |
| `INVALID_REQUEST` | 400 | Tipo de parámetro inválido (ej. UUID malformado) |
| `REPOSITORY_NOT_ACCESSIBLE` | 404 | Repo privado o no existe |
| `ANALYSIS_NOT_FOUND` | 404 | `id` no existe en BD |
| `JOB_NOT_FOUND` | 404 | `jobId` no existe (o fue purgado por retención) |
| `REPOSITORY_TOO_LARGE` | 413 | Excede `TOKENMETER_MAX_REPOSITORY_BYTES` (default 300 MiB) |
| `RATE_LIMITED` | 429 | Rate limiter por IP **o** techo de cola del executor (`tokenmeter.analyze-throttle.queue-capacity`, default `256`) alcanzado. La saturación de slots por sí sola **ya no devuelve 429**: se admite el job y se devuelve `202` con `status=QUEUED` (ver `POST /api/analyze`). |
| `CLONE_FAILED` | 502 | git CLI falló durante el clone |
| `CLONE_TIMEOUT` | 504 | Excedió `TOKENMETER_CLONE_TIMEOUT` (default 120s) |

> Desde el cambio `observable-analysis-jobs`, los códigos `CLONE_FAILED`, `CLONE_TIMEOUT`, `REPOSITORY_TOO_LARGE` y `ANALYSIS_FAILED` ya **no se devuelven como HTTP 5xx desde `POST /api/analyze`**: ahora viajan dentro del cuerpo del job (`status=FAILED`, `error.code`) y se exponen vía `GET /api/analyze/jobs/{jobId}`. El POST sólo puede fallar con `400 INVALID_URL` o `429 RATE_LIMITED`. El código de job `JOB_INTERRUPTED` aparece **solo dentro del body del job** (no como código HTTP) cuando el backend se reinicia con jobs en vuelo.

---

## `GET /api/health`

Health check.

**200 OK**

```json
{
  "status": "ok",
  "service": "tokenmeter-backend",
  "timestamp": "2026-05-09T10:15:30.123Z"
}
```

Endpoint Spring Actuator alternativo: `GET /actuator/health`.

También están disponibles probes Kubernetes-style: `GET /actuator/health/liveness` y `GET /actuator/health/readiness`.

---

## `GET /actuator/prometheus`

Endpoint de métricas Prometheus generado por Spring Actuator + Micrometer.

Pensado para ser consumido por un Prometheus externo al servidor de la app. Hay una configuración base en `deploy/prometheus/tokenmeter-scrape.yml` y un dashboard Grafana importable en `deploy/grafana/tokenmeter-backend-dashboard.json`.

**200 OK** (`text/plain; version=0.0.4`)

```text
# HELP jvm_info JVM version info
# TYPE jvm_info gauge
jvm_info{...} 1.0
```

---

## `POST /api/analyze`

Crea un **job de análisis** asíncrono. Valida la URL, persiste un registro `analysis_job` en estado `QUEUED` y entrega el trabajo a un `ThreadPoolTaskExecutor` dedicado. La respuesta es inmediata (< 100 ms en condiciones normales); el progreso se consulta mediante `GET /api/analyze/jobs/{jobId}`.

Este endpoint está sujeto a rate limiting (`AnalyzeRateLimitInterceptor` por IP) y al límite de capacidad de la cola del executor.

**Request**

```json
{
  "repositoryUrl": "https://github.com/guilu/tokenmeter"
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `repositoryUrl` | string | `@NotBlank`. Debe ser URL válida de `https://github.com/<owner>/<repo>` |

**202 Accepted**

```json
{
  "jobId": "0d4b8c8e-9a32-4d2a-9b58-6e9c1d6f7a01",
  "status": "QUEUED",
  "statusUrl": "/api/analyze/jobs/0d4b8c8e-9a32-4d2a-9b58-6e9c1d6f7a01",
  "analysisId": null
}
```

| Campo | Tipo | Notas |
|---|---|---|
| `jobId` | string (UUID) | Identificador estable del job. Sobrevive a reinicios. |
| `status` | enum | `QUEUED` \| `RUNNING` \| `SUCCESS` \| `FAILED`. En la respuesta inicial es siempre `QUEUED`. |
| `statusUrl` | string | Ruta relativa al endpoint de polling (`/api/analyze/jobs/{jobId}`). |
| `analysisId` | UUID \| null | Reservado para un futuro fast-path de cache (analysis ya existente). Actualmente siempre `null`. |

**Errores**

- `400 INVALID_URL` — URL ausente, malformada o no de `github.com`.
- `429 RATE_LIMITED` — exactamente dos causas:
  1. Rate limiter por IP (`AnalyzeRateLimitInterceptor`) excedido.
  2. Cola interna del executor (`tokenmeter.analyze-throttle.queue-capacity`, default `256`) llena: todos los slots `maxConcurrent` están ocupados **y** la cola ya tiene `queueCapacity` jobs encolados. `error.message` menciona explícitamente que la cola está al máximo.

  La saturación de slots **sin** que la cola esté llena **no** devuelve 429: el job se admite y se devuelve `202` con `status=QUEUED`. El cliente debe pollear `GET /api/analyze/jobs/{jobId}` para ver `queueState.queuePosition` decrecer.

Los errores que aparecían como HTTP 5xx en la versión síncrona (`CLONE_FAILED`, `CLONE_TIMEOUT`, `REPOSITORY_TOO_LARGE`, `ANALYSIS_FAILED`) ahora se materializan **dentro del body del job** (`status=FAILED`, `error.code`) y se exponen vía `GET /api/analyze/jobs/{jobId}`.

> El estado `QUEUED` significa que el job fue admitido y está esperando un worker libre. Con `queueCapacity = 256` (default) la API absorbe ráfagas grandes antes de devolver 429; sólo se devuelve 429 cuando los slots **y** la cola están llenos.

---

## `GET /api/analyze/jobs/{jobId}`

Devuelve el snapshot actual de un job de análisis. Diseñado para polling cada 1.5–2 s desde el cliente. **No** está sujeto al `AnalyzeRateLimitInterceptor` — un cliente puede pollear con la cadencia que necesite sin generar 429.

**Path params**: `jobId` (UUID).

**200 OK** — job esperando worker (`QUEUED`):

```json
{
  "jobId": "0d4b8c8e-9a32-4d2a-9b58-6e9c1d6f7a01",
  "status": "QUEUED",
  "phase": "QUEUED",
  "phaseLabel": "Waiting for an analysis slot",
  "progressPercent": 0,
  "message": null,
  "analysisId": null,
  "error": null,
  "metrics": null,
  "timestamps": {
    "createdAt": "2026-05-22T10:15:30.123Z",
    "startedAt": null,
    "updatedAt": "2026-05-22T10:15:30.123Z",
    "completedAt": null
  },
  "queueState": {
    "runningCount": 2,
    "maxConcurrency": 2,
    "queuePosition": 3
  }
}
```

`phaseLabel` cambia a `"Waiting for an analysis slot"` cuando `queueState.runningCount >= queueState.maxConcurrency`; en la ventana breve previa al `markStarted` puede aparecer `"Queued"` con `runningCount < maxConcurrency`.

**200 OK** — job activo (`RUNNING`):

```json
{
  "jobId": "0d4b8c8e-9a32-4d2a-9b58-6e9c1d6f7a01",
  "status": "RUNNING",
  "phase": "COUNTING_TOKENS",
  "phaseLabel": "Counting tokens",
  "progressPercent": 62,
  "message": "Tokenizing 142 files",
  "analysisId": null,
  "error": null,
  "metrics": {
    "filesDiscovered": 142,
    "filesProcessed": 88,
    "filesSkipped": 0,
    "tokensCounted": 51230,
    "contextWindows": null,
    "pricingModelsProcessed": null
  },
  "timestamps": {
    "createdAt": "2026-05-22T10:15:30.123Z",
    "startedAt": "2026-05-22T10:15:30.421Z",
    "updatedAt": "2026-05-22T10:15:42.812Z",
    "completedAt": null
  },
  "queueState": {
    "runningCount": 2,
    "maxConcurrency": 2,
    "queuePosition": null
  }
}
```

**200 OK** — job terminado con éxito (`SUCCESS`):

```json
{
  "jobId": "0d4b8c8e-9a32-4d2a-9b58-6e9c1d6f7a01",
  "status": "SUCCESS",
  "phase": "COMPLETED",
  "phaseLabel": "Completed",
  "progressPercent": 100,
  "message": null,
  "analysisId": "9f6c3a2e-4b1d-4d2a-9b58-6e9c1d6f7a01",
  "error": null,
  "metrics": {
    "filesDiscovered": 142,
    "filesProcessed": 142,
    "filesSkipped": 0,
    "tokensCounted": 95210,
    "contextWindows": 1,
    "pricingModelsProcessed": 17
  },
  "timestamps": {
    "createdAt": "2026-05-22T10:15:30.123Z",
    "startedAt": "2026-05-22T10:15:30.421Z",
    "updatedAt": "2026-05-22T10:15:54.118Z",
    "completedAt": "2026-05-22T10:15:54.118Z"
  },
  "pricing": {
    "snapshotId": "v1:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "primarySource": "REMOTE",
    "capturedAt": "2026-05-22T10:15:50.012Z"
  }
}
```

Cuando `status = SUCCESS`, `analysisId` apunta al recurso persistido — recuperable con `GET /api/analyze/{id}`. El bloque opcional `pricing` aparece desde `CALCULATING_COSTS`, una vez capturado el snapshot de precios; se omite por completo para jobs previos a esa fase y para filas legacy sin identidad.

**200 OK** — job terminado en fallo (`FAILED`):

```json
{
  "jobId": "0d4b8c8e-9a32-4d2a-9b58-6e9c1d6f7a01",
  "status": "FAILED",
  "phase": "FAILED",
  "phaseLabel": "Failed",
  "progressPercent": 35,
  "message": "Cloning repository",
  "analysisId": null,
  "error": {
    "code": "CLONE_TIMEOUT",
    "message": "git clone exceeded 120s"
  },
  "metrics": {
    "filesDiscovered": null,
    "filesProcessed": null,
    "filesSkipped": null,
    "tokensCounted": null,
    "contextWindows": null,
    "pricingModelsProcessed": null
  },
  "timestamps": {
    "createdAt": "2026-05-22T10:15:30.123Z",
    "startedAt": "2026-05-22T10:15:30.421Z",
    "updatedAt": "2026-05-22T10:17:30.812Z",
    "completedAt": "2026-05-22T10:17:30.812Z"
  }
}
```

**Schema**

| Campo | Tipo | Notas |
|---|---|---|
| `jobId` | string (UUID) | Mismo valor devuelto por `POST /api/analyze`. |
| `status` | enum | `QUEUED` \| `RUNNING` \| `SUCCESS` \| `FAILED`. |
| `phase` | enum | Fase actual. Valores: `QUEUED`, `CHECKING_CACHE`, `CLONING_REPOSITORY`, `SCANNING_FILES`, `FILTERING_FILES`, `COUNTING_TOKENS`, `CALCULATING_COSTS`, `SAVING_REPORT`, `COMPLETED`, `FAILED`. Forward-only en el happy path; cualquier fase no terminal puede saltar a `FAILED`. |
| `phaseLabel` | string | Etiqueta legible alineada con los 8 stages del frontend. |
| `progressPercent` | int [0..100] | Clampado a `[0..99]` mientras `status ≠ SUCCESS`. Llega a `100` **únicamente** cuando `status = SUCCESS` y `analysisId ≠ null`. |
| `message` | string \| null | Mensaje opcional asociado a la fase actual. |
| `analysisId` | UUID \| null | `null` mientras el job no haya completado con éxito. |
| `error` | object \| null | Presente sólo si `status = FAILED`. Sub-campos: `code` (`AnalysisJobErrorCode`), `message`. |
| `error.code` | enum | `CLONE_TIMEOUT`, `REPOSITORY_TOO_LARGE`, `INVALID_URL`, `ANALYSIS_FAILED`, `JOB_INTERRUPTED`, `RATE_LIMITED`. |
| `metrics` | object | Todos los campos son nullable hasta que la fase relevante los rellena. |
| `metrics.filesDiscovered` | long \| null | Archivos detectados tras el filtrado inicial. |
| `metrics.filesProcessed` | long \| null | Archivos contabilizados (invariante: ≤ `filesDiscovered`). |
| `metrics.filesSkipped` | long \| null | Archivos descartados (binarios, ignorados, etc.). |
| `metrics.tokensCounted` | long \| null | Tokens totales acumulados. |
| `metrics.contextWindows` | int \| null | Número de context windows aplicados. Usado por el frontend en `COUNTING_TOKENS`. |
| `metrics.pricingModelsProcessed` | int \| null | Modelos de pricing procesados durante `CALCULATING_COSTS`. |
| `timestamps.createdAt` | ISO-8601 | Inserción inicial (`status=QUEUED`). |
| `timestamps.startedAt` | ISO-8601 \| null | Primera transición a `RUNNING`. |
| `timestamps.updatedAt` | ISO-8601 | Último emit del progreso. |
| `timestamps.completedAt` | ISO-8601 \| null | Set cuando el job alcanza un estado terminal (`SUCCESS`/`FAILED`). |
| `queueState` | object \| null | Vista on-read del executor. Presente para `QUEUED` y `RUNNING`; **omitido o `null`** cuando `status ∈ {SUCCESS, FAILED}`. **No aparece** en el body de `POST /api/analyze`. |
| `queueState.runningCount` | int (≥ 0) | Filas en `analysis_job` con `status = RUNNING` en el instante del poll. |
| `queueState.maxConcurrency` | int (≥ 1) | Cap del sistema, leído de `tokenmeter.analyze-throttle.max-concurrent`. |
| `queueState.queuePosition` | int (≥ 1) \| null | Posición FIFO 1-based ordenando por `(created_at ASC, id ASC)`. **Presente sólo** cuando `status = QUEUED`; `null` cuando `status = RUNNING`. Es estimación best-effort: monótona decreciente en ausencia de nuevas submissions delante del job, pero un fallo en una posición previa puede hacerla saltar. |
| `pricing` | object \| ausente | Metadata opcional del snapshot de precios capturado en `CALCULATING_COSTS`. Omitido cuando `pricing_snapshot_id IS NULL`. |
| `pricing.snapshotId` | string | `v1:` + SHA-256 hex de la canonicalización de precios activa. |
| `pricing.primarySource` | enum | `OVERRIDE`, `REMOTE` o `FALLBACK`; capa ganadora de mayor precedencia en el snapshot capturado. |
| `pricing.capturedAt` | ISO-8601 | Instante en el que el worker capturó el snapshot para ese job. |

**404 JOB_NOT_FOUND** si el `jobId` no existe o fue purgado por el scheduler de retención (por defecto: jobs `SUCCESS` > 7 días, `FAILED` > 30 días).

> **Polling y rate limiting**: este endpoint está excluido del `AnalyzeRateLimitInterceptor` (`excludePathPatterns("/api/analyze/jobs/**", ...)`). El cliente oficial (`useAnalysisJob`) pollea cada 1.5 s con `setTimeout` encadenado y `AbortController`, detiene polling cuando `status ∈ {SUCCESS, FAILED}` o tras un 404.

> **Estado `JOB_INTERRUPTED`**: si el backend se reinicia con jobs en vuelo (`QUEUED`/`RUNNING`), un `AnalysisJobReaper` (`ApplicationRunner`) los reconcilia al boot marcándolos `FAILED` con `error.code = JOB_INTERRUPTED`. El cliente recibe el body de fallo en el siguiente poll.

---

## `GET /api/analyze/{id}`

Devuelve un análisis previamente persistido (resultado terminal de un job con `status=SUCCESS`).

**Path params**: `id` (UUID). Corresponde al `analysisId` que devolvió `GET /api/analyze/jobs/{jobId}` al completar.

**200 OK**

```json
{
  "id": "9f6c3a2e-4b1d-4d2a-9b58-6e9c1d6f7a01",
  "createdAt": "2026-05-22T10:15:54.118Z",
  "repositoryUrl": "https://github.com/guilu/tokenmeter",
  "status": "SUCCESS",
  "metrics": {
    "totalFiles": 142,
    "totalLines": 8421,
    "totalBytes": 312045,
    "tokenEncoding": "o200k_base",
    "totalTokens": 95210,
    "languages": {
      "Java": { "language": "Java", "files": 73, "lines": 5210, "bytes": 198213, "tokens": 60123 },
      "TypeScript": { "language": "TypeScript", "files": 12, "lines": 850, "bytes": 25430, "tokens": 9821 }
    }
  },
  "costEstimates": [
    {
      "provider": "anthropic",
      "model": "claude-3-5-sonnet",
      "mode": "raw",
      "baseTokens": 95210,
      "estimatedInputTokens": 0,
      "estimatedOutputTokens": 95210,
      "inputCost": 0.000000,
      "outputCost": 1.428150,
      "totalCost": 1.428150,
      "formula": "inputCost=(baseTokens*0*inputPricePerMillion)/1_000_000; outputCost=(baseTokens*1*outputPricePerMillion)/1_000_000; totalCost=inputCost+outputCost"
    }
  ],
  "pricing": {
    "snapshotId": "v1:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "primarySource": "REMOTE",
    "capturedAt": "2026-05-22T10:15:50.012Z"
  }
}
```

`costEstimates` contiene `N modelos × 3 modos` entradas (modos `raw`, `assisted`, `agentic`). El bloque `pricing` es opcional y se omite por completo para análisis legacy con `analysis.pricing_snapshot_id IS NULL`.

**404 ANALYSIS_NOT_FOUND** si el `id` no existe.

---

## `GET /api/analyze/{id}/cost-breakdown`

Vista alternativa del análisis enfocada en el desglose de costes (sin métricas de archivos), pensada para tablas/dashboards.

**200 OK**

```json
{
  "analysisId": "9f6c3a2e-4b1d-4d2a-9b58-6e9c1d6f7a01",
  "totalTokens": 95210,
  "summary": {
    "cheapestProvider": "deepseek",
    "cheapestModel": "deepseek-chat",
    "cheapestTotalCost": 0.104731
  },
  "models": [
    {
      "provider": "anthropic",
      "model": "claude-3-5-sonnet",
      "pricing": { "inputTokenPricePerMillion": 3.00, "outputTokenPricePerMillion": 15.00 },
      "modes": [
        { "mode": "raw", "baseTokens": 95210, "estimatedInputTokens": 0, "estimatedOutputTokens": 95210,
          "inputCost": 0.0, "outputCost": 1.428150, "totalCost": 1.428150 }
      ]
    }
  ],
  "pricing": {
    "snapshotId": "v1:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "primarySource": "REMOTE",
    "capturedAt": "2026-05-22T10:15:50.012Z"
  }
}
```

El bloque `pricing` tiene la misma semántica que en `GET /api/analyze/{id}` y se omite para filas legacy sin `pricing_snapshot_id`.

**404 ANALYSIS_NOT_FOUND** si `id` no existe.

---

## `GET /api/pricing`

Devuelve los precios vigentes, fusionando overrides → snapshot persistido (REMOTE) → fallback YAML. Precios en USD por millón de tokens.

**200 OK**

```json
{
  "lastRefreshedAt": "2026-05-15T03:00:00Z",
  "primarySource": "litellm",
  "models": [
    {
      "provider": "anthropic",
      "model": "claude-opus-4-7",
      "inputTokenPricePerMillion": 15.00,
      "outputTokenPricePerMillion": 75.00,
      "source": "REMOTE",
      "fetchedAt": "2026-05-15T03:00:00Z",
      "externalModelId": "claude-opus-4-7"
    },
    {
      "provider": "deepseek",
      "model": "deepseek-chat",
      "inputTokenPricePerMillion": 0.27,
      "outputTokenPricePerMillion": 1.10,
      "source": "FALLBACK",
      "fetchedAt": "2026-05-15T02:58:14Z"
    }
  ]
}
```

| Campo | Tipo | Notas |
|---|---|---|
| `lastRefreshedAt` | ISO-8601 \| null | Máximo `fetchedAt` entre filas con `source=REMOTE`. `null` si ninguna refresh remota ha tenido éxito |
| `primarySource` | `"litellm"` \| `"fallback"` \| `"mixed"` | `"litellm"` si todas las filas son REMOTE; `"fallback"` si todas son FALLBACK; `"mixed"` en cualquier otro caso (incluye OVERRIDE) |
| `models[].source` | `"REMOTE"` \| `"FALLBACK"` \| `"OVERRIDE"` | Capa ganadora para esa fila |
| `models[].fetchedAt` | ISO-8601 | Siempre presente |
| `models[].externalModelId` | string \| ausente | Clave LiteLLM (`litellm_provider/model`) para trazabilidad; ausente para FALLBACK/OVERRIDE |

Ordenado ascendente por `provider.configKey()` y luego por `model` (lexicográfico). El endpoint devuelve `200` incluso si no se ha completado nunca una refresh remota; en ese caso `primarySource=="fallback"` y `lastRefreshedAt` es `null`.

---

## `POST /api/admin/pricing/refresh`

Dispara una refresh sincrónica del pipeline de pricing (LiteLLM → mapeo → persistencia transaccional). Endpoint con feature-flag.

**Request**: cuerpo vacío.

**202 Accepted**

```json
{
  "fetchedAt": "2026-05-15T03:00:00Z",
  "updated": 17,
  "skipped": 0,
  "failed": 0
}
```

| Campo | Tipo | Significado |
|---|---|---|
| `fetchedAt` | ISO-8601 | Instante de finalización de la refresh |
| `updated` | int ≥ 0 | Filas escritas como `REMOTE` |
| `skipped` | int ≥ 0 | Mapeos configurados sin contrapartida en el payload upstream |
| `failed` | int ≥ 0 | Reservado para fallos parciales; en v1 una refresh o cuaja entera o tira excepción |

**503 Service Unavailable**

Se devuelve cuando:
- `tokenmeter.pricing.admin.enabled=false` (endpoint deshabilitado por configuración). No se contacta upstream ni se modifica ninguna fila.
- El upstream falla (timeout, 5xx, payload vacío) o la persistencia falla. Cuerpo:

```json
{ "error": "pricing_refresh_failed", "message": "Failed to fetch LiteLLM pricing" }
```

---

## `POST /api/repositories/intake` (legacy)

Endpoint inicial que solo clona y valida un repo, sin tokenizar ni estimar costes. Mantenido para compatibilidad.

**Request**

```json
{ "repositoryUrl": "https://github.com/guilu/tokenmeter" }
```

**201 Created**: `RepositoryIntakeResult` con metadatos básicos del clone (owner, name, totalBytes, etc).

Para uso normal, preferir `POST /api/analyze`.

---

## Modos de coste

Definidos en `domain/cost/CostEstimationMode`. Multiplicadores aplicados a `baseTokens` (los tokens del código fuente del repo):

| `mode` (JSON) | output × base | input × base |
|---|---|---|
| `raw` | 1 | 0 |
| `assisted` | 5 | 1 |
| `agentic` | 20 | 4 |

`totalCost` se calcula como:

```
inputCost  = baseTokens × inputMul  × inputTokenPricePerMillion  / 1_000_000
outputCost = baseTokens × outputMul × outputTokenPricePerMillion / 1_000_000
totalCost  = inputCost + outputCost   (HALF_UP, 6 decimales)
```

---

## Ejemplos cURL

```bash
# Health
curl -s http://localhost:8080/api/health

# Encolar un análisis → 202 con jobId + statusUrl
curl -s -X POST http://localhost:8080/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"repositoryUrl":"https://github.com/guilu/tokenmeter"}'

# Pollear el estado del job
curl -s http://localhost:8080/api/analyze/jobs/0d4b8c8e-9a32-4d2a-9b58-6e9c1d6f7a01

# Recuperar el análisis terminal (cuando job.status=SUCCESS y job.analysisId != null)
curl -s http://localhost:8080/api/analyze/9f6c3a2e-4b1d-4d2a-9b58-6e9c1d6f7a01

# Pricing
curl -s http://localhost:8080/api/pricing

# Refresh manual del pricing (requiere tokenmeter.pricing.admin.enabled=true)
curl -s -X POST http://localhost:8080/api/admin/pricing/refresh
```
