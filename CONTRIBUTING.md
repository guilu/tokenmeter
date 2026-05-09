# Contributing to TokenMeter

¡Gracias por querer contribuir! Este documento describe cómo proponer cambios.

## Antes de empezar

- Lee [`README.md`](./README.md) para entender el proyecto.
- Lee [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) para entender la arquitectura hexagonal.
- Lee [`CLAUDE.md`](./CLAUDE.md) si vas a usar un asistente IA.
- Issues abiertos en [GitHub Issues](https://github.com/guilu/tokenmeter/issues).

## Setup local

### Requisitos

- Java 21 (Temurin recomendado)
- Node.js 22+
- Docker + Docker Compose
- PostgreSQL 18 (o Docker)

### Arrancar todo el stack

```bash
docker compose up --build -d
```

Servicios: frontend `:3000`, backend `:8080`, postgres `:5432`.

### Desarrollo backend

```bash
cd backend
./gradlew bootRun           # arranca con perfil `local` (db local en :5432)
./gradlew test              # tests
./gradlew check             # checkstyle + spotless + tests
./gradlew spotlessApply     # autoformat (correr antes de commit)
```

### Desarrollo frontend

```bash
cd frontend
npm ci
npm run dev                 # vite :3000, proxy /api → :8080
npm run lint
npm run build
npm run format              # prettier
```

## Workflow de contribución

1. **Abre un issue** describiendo el bug o la feature antes de codear cambios grandes.
2. **Fork + branch** descriptiva: `feat/cost-breakdown-export`, `fix/clone-timeout-windows`.
3. **Commits** siguen el formato gitmoji + conventional commits (ver abajo).
4. **Tests** obligatorios para cambios en `application/` o `domain/`.
5. **Spotless + lint** verdes (`./gradlew check` y `npm run lint`).
6. **PR** contra `main` con descripción clara de qué cambia y por qué.

## Convención de commits

Formato:

```
<gitmoji> <type>(<scope>): <description>
```

Ejemplos:

```
✨ feat(analyzer): add commit-sha caching layer
🐛 fix(api): handle empty repositoryUrl on POST /api/analyze
♻️ refactor(domain): extract token counter port out of OpenAiTokenCounter
🧪 test(cost): cover AGENTIC mode rounding for huge repos
📝 docs(api): document /api/pricing response shape
🔧 chore(deps): bump spring boot to 3.5.7
🗃️ db(migration): V4 add repository_commit_sha column
```

Gitmojis comunes: ✨ feat · 🐛 fix · ♻️ refactor · 🧪 test · 📝 docs · 🔧 chore · 🚀 perf · 💄 style · 🔒 security · 🗃️ db.

## Convenciones de código

### Java / Spring (backend)

- Google Java Format (Spotless lo aplica). Correr `./gradlew spotlessApply`.
- Inyección **por constructor**, nunca `@Autowired` por campo.
- DTOs son `record`. Nada de Lombok.
- Errores HTTP → lanzar `RepositoryIntakeException(errorCode, msg)`. Nunca `ResponseStatusException`.
- Migraciones Flyway: nuevo `V<N+1>__descripcion.sql`. **Nunca** editar migraciones aplicadas.
- Mappers a mano en `infrastructure/web/<feature>/`.
- Sin lógica de negocio en `infrastructure/`. Si un test fuerza eso, refactorizar a `application/`.

### TypeScript / React (frontend)

- Prettier + ESLint configurados. Correr `npm run lint` antes del commit.
- `import type` para tipos.
- `services/api.ts` centraliza llamadas HTTP. Tipos en `types/api.ts` reflejan el contrato del backend.
- Tailwind 4 — utilidades inline, sin `@apply` salvo excepción justificada.

## Testing

### Backend

```bash
cd backend
./gradlew test
./gradlew test --tests "*CostEstimation*"
```

- Unit tests sin Spring siempre que se pueda (más rápidos).
- Tests de controllers usan `@SpringBootTest` o `@WebMvcTest`.
- Tests con BD usan H2 (perfil test, ver `backend/src/test/resources/application.yml`).
- Cualquier endpoint nuevo debe tener test en `RepositoryAnalysisControllerTest` o equivalente.

### Frontend

Aún no hay suite de tests instalada. Contribuciones para añadir Vitest + Testing Library son bienvenidas.

## Añadir un modelo de pricing

1. Añadir entrada en `backend/src/main/resources/pricing.yaml`:
   ```yaml
   - provider: openai
     model: gpt-4o-mini
     input-token-price: 0.15
     output-token-price: 0.60
   ```
2. Si el provider no existía, añadirlo a `dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider`.
3. Añadir caso al `YamlPricingProviderTest`.

## Añadir un endpoint

1. Crear `<Feature>Controller` en `infrastructure/web/<feature>/`.
2. DTOs como `record` en el mismo paquete.
3. Mapper si hay traducción no trivial.
4. Test con `@WebMvcTest` o `@SpringBootTest`.
5. Documentar en `docs/API.md`.

## Estructura del repo

```
tokenmeter/
├── backend/                Spring Boot (Java 21, Gradle KTS)
│   ├── src/main/java/dev/diegobarrioh/tokenmeter/
│   │   ├── domain/         núcleo de negocio
│   │   ├── application/    casos de uso + ports
│   │   └── infrastructure/ adapters: web, persistence, git, pricing
│   └── src/main/resources/ application.yml, pricing.yaml, db/migration/
├── frontend/               React 19 + Vite 8 + Tailwind 4
│   └── src/
├── docs/                   ARCHITECTURE.md, API.md, assets/
├── .github/workflows/      ci.yml
├── docker-compose.yml
├── CLAUDE.md
├── CONTRIBUTING.md         (este archivo)
└── README.md
```

## Code review

Toda PR requiere:

- CI verde (backend + frontend + sonar).
- Al menos 1 review approve.
- Sin secrets, sin `.env`, sin binarios > 1 MB.

## Reportar bugs de seguridad

No abras un issue público. Escribe a `diegobarrioh@gmail.com` con detalles + PoC si es posible.

## Licencia

MIT. Al contribuir aceptas que tu código se publique bajo esta licencia.
