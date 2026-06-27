# Logging — Loki / Grafana

TokenMeter's backend emits **structured JSON logs** (one JSON object per line) on the `docker`
and `prod` profiles, designed to be shipped to a central Loki and explored in Grafana. The `local`
and `test` profiles keep a human-readable plain console.

## Pipeline

```
backend container (stdout JSON)
        │  Docker json-file driver
        ▼
Promtail (~/monitoring-promtail on omarchy)
        │  relabels container labels → stream labels
        ▼
Loki (red.local:3100)  ◀── Grafana (red.local:3000)
```

`host=omarchy` is added by Promtail. `app` / `component` come from the Docker container **labels**
declared in `docker-compose.yml`. Everything else lives **inside** the JSON line as fields.

## Labels vs fields (cardinality matters)

Loki indexes by **labels**. Keep the label set tiny and stable — every distinct label value is a
new stream. Rich, high-cardinality data stays as JSON **fields** (queryable via `| json` at read
time) or in the message.

| Stream labels (low cardinality — OK) | Source |
|---|---|
| `host` | Promtail (host machine, e.g. `omarchy`) |
| `app` = `tokenmeter` | container label (`docker-compose.yml`) |
| `component` = `backend` / `frontend` / `db` | container label |

| JSON fields (NOT labels) | Source |
|---|---|
| `level`, `logger_name`, `thread_name`, `message`, `@timestamp` | LogstashEncoder |
| `service` (`tokenmeter-backend`), `component`, `environment` (active profile) | `logback-spring.xml` custom fields |
| `requestId` | `RequestCorrelationFilter` (MDC, per HTTP request) |
| `jobId` | analysis job MDC (`JpaAnalysisJobProgressEmitter`) |

### Do NOT turn these into labels

They are unbounded or per-event and would explode Loki's stream cardinality:

- `repositoryUrl` (full clone URL) — keep in the message/field only.
- `analysisId`, `jobId` — keep as JSON fields, query with `| json | jobId="…"`.
- client IPs, dynamic request paths, user agents — fields/message only.

## Correlation ids

- **HTTP**: `RequestCorrelationFilter` runs first in the chain. It reads an inbound
  `X-Request-Id` header when present and safe (`[A-Za-z0-9._-]{1,64}`), otherwise generates a UUID,
  exposes it in the MDC as `requestId`, and echoes it back in the `X-Request-Id` response header.
- **Analysis jobs**: each progress transition logs with `jobId` in the MDC (set by
  `JpaAnalysisJobProgressEmitter` via `MdcScope`), so a single analysis can be traced across phases.

## Recommended LogQL queries

Errors and warnings from the backend:

```logql
{app="tokenmeter", component="backend"} | json | level=~"ERROR|WARN"
```

Everything from this host's TokenMeter stack:

```logql
{host="omarchy", app="tokenmeter"}
```

Trace one analysis job across phases:

```logql
{app="tokenmeter", component="backend"} | json | jobId="<job-uuid>"
```

Follow a single HTTP request:

```logql
{app="tokenmeter", component="backend"} | json | requestId="<request-id>"
```

Pricing refresh activity:

```logql
{app="tokenmeter", component="backend"} | json | logger_name=~".*[Pp]ricing.*"
```

Error rate over time (for a Grafana panel):

```logql
sum(count_over_time({app="tokenmeter", component="backend"} | json | level="ERROR" [5m]))
```

## Promtail note (host side, not in this repo)

Promtail must (1) relabel the Docker container labels `app` / `component` into Loki stream labels,
and (2) parse the JSON so `level`, `requestId`, `jobId`, etc. are filterable with `| json`. Adding
new container labels here is the supported way to add stable Loki labels — do not rely on the
container name.
