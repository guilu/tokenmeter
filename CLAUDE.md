# CLAUDE.md

Guía para Claude Code (y otros asistentes IA) trabajando en este repo.

## Qué es TokenMeter

Servicio que clona un repositorio público de GitHub, cuenta tokens por archivo (encoder OpenAI `o200k_base` vía `jtokkit`) y estima el coste de "regenerarlo" con IA usando precios reales de varios modelos. El cálculo es un **suelo**, no un techo: no incluye prompts de entrada, intentos fallidos ni razonamiento más allá de los multiplicadores definidos en `CostEstimationMode`.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Gradle Kotlin DSL |
| Persistencia | PostgreSQL 18 + Flyway (migraciones `V1`–`V3`) |
| Tokenizer | `com.knuddels:jtokkit` (encoder `O200K_BASE`) |
| Clone | `git` CLI |
| Frontend | React 19, Vite 8, TypeScript 6, Tailwind 4 |
| Tests | JUnit 5, Spring Boot Test, H2 (runtime test) |
| Calidad | Checkstyle, Spotless (Google Java Format), ESLint, Prettier, SonarCloud |
| Infra dev | Docker Compose (`db`, `backend`, `frontend`) |

## Comandos esenciales

### Backend (`cd backend`)

```bash
./gradlew build                # compila + tests + spotlessCheck
./gradlew check                # checkstyle + spotless + tests
./gradlew test                 # solo tests
./gradlew spotlessApply        # autoformat
./gradlew bootRun              # arranca local (perfil `local`, db en localhost:${TOKENMETER_DB_PORT:-5433})
```

### Frontend (`cd frontend`)

```bash
npm ci
npm run dev                    # vite dev server, puerto 3000, proxy /api → :8080
npm run build                  # tsc -b && vite build
npm run lint
npm run format
```

### Stack completo

```bash
docker compose up --build -d   # frontend :3001, backend :8081; db interno sin puerto host
```

## Arquitectura (hexagonal)

Tres paquetes en `backend/src/main/java/dev/diegobarrioh/tokenmeter/`:

- `domain/` — value objects, enums, records de negocio. Sin dependencias de Spring ni JPA. Ejemplos: `GitHubRepositoryUrl`, `CostEstimationMode`, `ModelPricing`, `RepositoryScanResult`.
- `application/` — casos de uso y orquestación. Servicios `@Service`, sin anotaciones JPA ni `@RestController`. Ejemplos: `RepositoryAnalysisService` (clone→scan→tokenize→estimate→persist), `RepositoryCostEstimationService`, `RepositoryFileScanner`.
- `infrastructure/` — adapters: `web/` (REST controllers, mappers, DTO), `persistence/` (entidades JPA, repos), `git/` (`GitCliRepositoryCloner`), `pricing/` (`YamlPricingProvider`).

**Regla**: dependencias siempre apuntan hacia adentro. `infrastructure` → `application` → `domain`. Nunca al revés.

Detalle completo: `docs/ARCHITECTURE.md`.

## Flujo de análisis

`POST /api/analyze` → `RepositoryAnalysisService.analyze`:

1. `GitHubRepositoryUrl.parse` valida URL.
2. `GitCliRepositoryCloner.clone` con timeout (`tokenmeter.repository-intake.clone-timeout`, 120s default).
3. `RepositorySizeCalculator.summarize` + `enforceSizeLimit` (max 300 MiB default).
4. `RepositoryFileScanner.scan` ignora `.git`, `node_modules`, `target`, `build`, `dist`, `coverage`. `BinaryFileDetector` filtra binarios.
5. `RepositoryTokenizationService.tokenize` por archivo con `OpenAiTokenCounter`.
6. `RepositoryCostEstimationService.estimate` calcula 3 modos × N modelos.
7. `JpaAnalysisPersistenceService.save` → tablas `analysis`, `language_stats`, `cost_estimates`.
8. `finally` → `deleteRecursively` del temp dir.

## Modos de coste (canónico, código)

| Modo | output ×base | input ×base |
|---|---|---|
| `RAW` | 1 | 0 |
| `ASSISTED` | 5 | 1 |
| `AGENTIC` | 20 | 4 |

Definidos en `domain/cost/CostEstimationMode.java`. Si los multiplicadores cambian, actualizar también README + tests + ARCHITECTURE.md.

Fórmula: `cost = (tokens × multiplicador × precioPorMillón) / 1_000_000`, redondeo `HALF_UP` a 6 decimales.

## Pricing

Configurado en `backend/src/main/resources/pricing.yaml`. Los precios son **por millón de tokens en USD**. Para añadir un modelo:

1. Añadir entrada en `pricing.yaml`.
2. Si es un provider nuevo, añadir constante en `domain/pricing/AiProvider`.
3. Test en `YamlPricingProviderTest`.

## Convenciones de código

- **Java**: Google Java Format (Spotless lo aplica). 2 espacios. Imports ordenados sin wildcards. Sin Lombok — usar `record` y constructores explícitos.
- **TypeScript**: Prettier + ESLint. Import types con `import type`.
- **Errores HTTP**: lanzar `RepositoryIntakeException` con `RepositoryIntakeErrorCode`. `RepositoryIntakeExceptionHandler` mapea a status correcto. No lanzar `ResponseStatusException` directamente.
- **Tests**: H2 en perfil test (`backend/src/test/resources/application.yml`). Tests de servicio sin Spring siempre que se pueda.
- **DTO**: records en `infrastructure/web/<feature>/`. Mappers separados (`RepositoryAnalysisMapper`, `CostBreakdownMapper`).
- **Migraciones Flyway**: nuevo archivo `V<N>__descripcion.sql` en `backend/src/main/resources/db/migration/`. Nunca editar migraciones aplicadas.

## Convención de commits

**Gitmoji + Conventional Commits**:

```
<gitmoji> <type>(<scope>): <description>
```

Ejemplos reales del repo:
```
✨ feat: expose cost breakdown API
💄 feat(frontend): improve mobile cost table formatting
🐛 fix: duplicate header in README.md
```

Gitmojis comunes: ✨ feat · 🐛 fix · ♻️ refactor · 🧪 test · 📝 docs · 🔧 chore · 🚀 perf · 💄 style · 🔒 security · 🗃️ db.

## Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local` / `docker` / `prod` |
| `TOKENMETER_BIND_ADDRESS` | `127.0.0.1` | IP host donde publicar frontend/backend |
| `TOKENMETER_FRONTEND_PORT` | `3001` | Puerto host del frontend Docker |
| `TOKENMETER_BACKEND_PORT` | `8081` | Puerto host del backend Docker |
| `TOKENMETER_DB_NAME` | `tokenmeter` | Nombre de la BBDD PostgreSQL (Docker) |
| `TOKENMETER_DB_USER` | **obligatorio** | Usuario PostgreSQL (Docker). `docker compose up` falla si no está definido. |
| `TOKENMETER_DB_PASSWORD` | **obligatorio** | Contraseña PostgreSQL (Docker). Generar con `openssl rand -base64 32`. `docker compose up` falla si no está definida. |
| `TOKENMETER_WORKDIR` | `${java.io.tmpdir}/tokenmeter-repositories` | Directorio temporal para clones |
| `TOKENMETER_MAX_REPOSITORY_BYTES` | `314572800` (300 MiB) | Tamaño máximo del repo |
| `TOKENMETER_CLONE_TIMEOUT` | `120s` | Timeout de clone |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | — | Sobrescritura explícita datasource |

## No-go zones para asistentes IA

- **No editar migraciones Flyway ya aplicadas** (`V1`, `V2`, `V3`). Crear `V4` o superior.
- **No añadir dependencias** sin justificación clara — el proyecto es deliberadamente delgado.
- **No introducir Lombok**, MapStruct ni generadores. Mappers a mano.
- **No mover lógica de negocio a `infrastructure`**. Si un test necesita cambiar `infrastructure`, probablemente la lógica debería estar en `application` o `domain`.
- **No commit de `.env`, secrets ni archivos en `build/`/`node_modules/`/`.gradle/`**.
- **No saltarse `spotlessCheck`** — CI lo ejecuta. Correr `./gradlew spotlessApply` antes de commit.
- **No añadir endpoints sin test** en `RepositoryAnalysisControllerTest` o equivalente.
- **No usar `@Autowired` por campo**. Inyección por constructor siempre.

## Testing checklist antes de PR

```bash
cd backend && ./gradlew clean check
cd frontend && npm run lint && npm run build
docker compose up --build -d  # smoke test si tocas wiring
```

## Endpoints (ver `docs/API.md`)

- `GET  /api/health`
- `POST /api/analyze`
- `GET  /api/analyze/{id}`
- `GET  /api/analyze/{id}/cost-breakdown`
- `GET  /api/pricing`
- `POST /api/repositories/intake` (legacy intake — usado solo para clonar/validar URL sin análisis completo)

## Estado

MVP en desarrollo activo. Roadmap en README. Persistencia, scan, tokenización y estimación funcionan; tokenizers reales por proveedor, badges, exportación CSV y API pública están pendientes.
