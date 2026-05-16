# Runbook

Operativa del backend de TokenMeter. Cubre el pipeline de pricing dinámico (cambio `dynamic-pricing-fetch`).

## Pricing refresh — métricas

El backend expone métricas Micrometer/Prometheus en `GET /actuator/prometheus`:

| Métrica | Tipo | Significado |
|---|---|---|
| `tokenmeter_pricing_refresh_success_total` | counter | Incrementa una vez por cada refresh REMOTE que persiste filas |
| `tokenmeter_pricing_refresh_failure_total` | counter | Incrementa por cada `PricingFetchException` o fallo de persistencia. La transacción NO se aplica — las filas previas sobreviven |
| `tokenmeter_pricing_refresh_last_success_timestamp_seconds` | gauge | Epoch (s) del último refresh exitoso. Útil para alertar sobre staleness |

Alerta sugerida: **2 fallos consecutivos en una ventana de 24h** (Prometheus rule sobre `rate(tokenmeter_pricing_refresh_failure_total[24h]) > 0` correlado con la ausencia de nuevos `_success_total`). El cron por defecto es semanal; si fallan dos semanas seguidas conviene investigar antes de que el snapshot REMOTE se desactualice más de un mes.

## Refresh manual

Cuando hay sospecha de que el catálogo está obsoleto o se acaba de actualizar `pricing-mapping.yaml`:

```bash
curl -s -X POST http://localhost:8081/api/admin/pricing/refresh
```

Pre-requisitos:

- `tokenmeter.pricing.admin.enabled=true` en el perfil activo. **Off por defecto en `prod`** hasta que el endpoint tenga auth. Si está off, la respuesta es `503` con `{"error":"pricing_refresh_disabled"}` y no se contacta upstream.
- Conectividad a `raw.githubusercontent.com`.

Respuestas:

- `202 Accepted` con `{fetchedAt, updated, skipped, failed}` — éxito.
- `503` con `{"error":"pricing_refresh_failed","message":"…"}` — fallo upstream o de persistencia. Filas previas intactas.

Verificación post-refresh:

```bash
curl -s http://localhost:8081/api/pricing | jq '.primarySource, .lastRefreshedAt'
```

`primarySource` debería ser `"litellm"` (o `"mixed"` si hay overrides activos) y `lastRefreshedAt` un instante reciente.

## Rollback

Por orden de menor a mayor impacto. Detalle completo en `openspec/changes/dynamic-pricing-fetch/design.md §10`.

1. **Desactivar el cron**: `tokenmeter.pricing.refresh.enabled=false` y reiniciar. El scheduler deja de dispararse; el snapshot persistido sigue sirviendo `/api/pricing`.
2. **Desactivar el endpoint admin**: `tokenmeter.pricing.admin.enabled=false`. El endpoint devuelve `503` sin tocar upstream.
3. **Revertir `@Primary` al `YamlPricingProvider`**: cambio de una línea (quitar `@Primary` de `CompositePricingProvider`, restaurarlo en `YamlPricingProvider`). Restaura el comportamiento pre-cambio sin tocar el schema.
4. **Abandono total del feature**: añadir `V6__drop_model_pricing.sql` con `DROP TABLE IF EXISTS model_pricing;` **sólo** tras los pasos 1–3 desplegados y consenso del equipo.

## Smoke test recipe

El smoke test completo requiere las credenciales obligatorias `TOKENMETER_DB_USER` y `TOKENMETER_DB_PASSWORD` (ver `README.md`). Receta manual:

```bash
# 1. Exportar secretos (generar password fuerte si no existe)
export TOKENMETER_DB_USER=tokenmeter
export TOKENMETER_DB_PASSWORD="$(openssl rand -base64 32)"

# 2. Levantar el stack
docker compose up --build -d

# 3. Sanity checks
curl -fsS http://localhost:8081/api/health
curl -fsS http://localhost:8081/api/pricing | jq '.primarySource, (.models | length)'

# 4. (Opcional) refresh manual si admin.enabled=true
curl -fsS -X POST http://localhost:8081/api/admin/pricing/refresh | jq

# 5. Limpiar
docker compose down
```

El primer arranque sobre BBDD vacía debe sembrar 17 filas FALLBACK y responder `/api/pricing` con `primarySource="fallback"` y `lastRefreshedAt=null` hasta que la primera refresh REMOTE complete.
