# TokenMeter

TokenMeter foundation repository.

## Structure

- `backend/` — Java 21 + Spring Boot 3 + Gradle Kotlin DSL, PostgreSQL, Flyway, hexagonal architecture base.
- `frontend/` — React + Vite + TypeScript + TailwindCSS.
- `.github/workflows/ci.yml` — backend/frontend CI, lint and optional SonarCloud analysis.

## Local startup

### Full stack with Docker Compose

```bash
docker compose up --build
```

Services:

- Frontend: <http://localhost:3000>
- Backend: <http://localhost:8080>
- PostgreSQL: `localhost:5432`

### Backend only

```bash
cd backend
./gradlew clean check
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

### Frontend only

```bash
cd frontend
npm install
npm run dev
```

## Quality gates

```bash
cd backend && ./gradlew clean check
cd frontend && npm ci && npm run lint && npm run build
```

## Jira

Foundation tasks:

- TKM-7 — Backend Foundation & Infrastructure Setup
- TKM-8 — Frontend Foundation & UI Base Setup
- TKM-9 — DevOps, Quality & CI/CD Setup
