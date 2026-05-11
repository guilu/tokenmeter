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
| `REPOSITORY_TOO_LARGE` | 413 | Excede `TOKENMETER_MAX_REPOSITORY_BYTES` (default 300 MiB) |
| `CLONE_FAILED` | 502 | git CLI falló durante el clone |
| `CLONE_TIMEOUT` | 504 | Excedió `TOKENMETER_CLONE_TIMEOUT` (default 120s) |

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

Clona un repositorio público de GitHub, lo escanea, cuenta tokens y calcula estimaciones de coste para todos los modelos × modos. Persiste el resultado y devuelve un `id` reutilizable.

**Request**

```json
{
  "repositoryUrl": "https://github.com/guilu/tokenmeter"
}
```

| Campo | Tipo | Validación |
|---|---|---|
| `repositoryUrl` | string | `@NotBlank`. Debe ser URL válida de `https://github.com/<owner>/<repo>` |

**200 OK**

```json
{
  "id": "9f6c3a2e-4b1d-4d2a-9b58-6e9c1d6f7a01",
  "createdAt": "2026-05-09T10:15:30.123Z",
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
  ]
}
```

`costEstimates` contiene `N modelos × 3 modos` entradas (modo `raw`, `assisted`, `agentic`).

**Errores**: `INVALID_URL` (400), `REPOSITORY_NOT_ACCESSIBLE` (404), `REPOSITORY_TOO_LARGE` (413), `CLONE_FAILED` (502), `CLONE_TIMEOUT` (504).

---

## `GET /api/analyze/{id}`

Devuelve un análisis previamente persistido.

**Path params**: `id` (UUID).

**200 OK**: misma forma que `POST /api/analyze`.

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
  ]
}
```

> Forma exacta dependiente de `CostBreakdownMapper`. Verificar `CostBreakdownResponse` para el contrato canónico.

**404 ANALYSIS_NOT_FOUND** si `id` no existe.

---

## `GET /api/pricing`

Devuelve los precios configurados en `pricing.yaml`. Precios en USD por millón de tokens.

**200 OK**

```json
{
  "models": [
    { "provider": "anthropic", "model": "claude-3-5-sonnet", "inputTokenPricePerMillion": 3.00, "outputTokenPricePerMillion": 15.00 },
    { "provider": "deepseek",  "model": "deepseek-chat",     "inputTokenPricePerMillion": 0.27, "outputTokenPricePerMillion": 1.10 },
    { "provider": "google",    "model": "gemini-1.5-pro",    "inputTokenPricePerMillion": 1.25, "outputTokenPricePerMillion": 5.00 },
    { "provider": "openai",    "model": "gpt-4o",            "inputTokenPricePerMillion": 2.50, "outputTokenPricePerMillion": 10.00 }
  ]
}
```

Ordenado por `provider` (alfabético) y luego `model`.

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

# Analizar un repo
curl -s -X POST http://localhost:8080/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"repositoryUrl":"https://github.com/guilu/tokenmeter"}'

# Recuperar análisis previo
curl -s http://localhost:8080/api/analyze/9f6c3a2e-4b1d-4d2a-9b58-6e9c1d6f7a01

# Pricing
curl -s http://localhost:8080/api/pricing
```
