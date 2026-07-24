# Deployment Guide

This document covers environment configuration, required secrets, and the deploy process for TripFlowAI's backend.

## Profiles

The application uses three Spring profiles:

| Profile | File | Purpose |
|---|---|---|
| `dev` | `application-dev.properties` | Local development (default when `SPRING_PROFILES_ACTIVE` is unset) |
| `test` | `application-test.properties` | CI and local test runs (Surefire + Failsafe) |
| `prod` | `application-prod.properties` | Production deployment (Render/Railway) |

Shared configuration (JWT settings, ORS client config, JPA dialect, `ddl-auto=validate`, actuator health exposure) lives in the base `application.properties` and is inherited by all profiles unless explicitly overridden.

**`ddl-auto` is always `validate`, never `update` or `create`, in every environment.** This was a deliberate fix after an early schema-drift incident where `update` silently masked mismatches between Flyway migrations and JPA entities. Schema changes must go through a Flyway migration — the application will fail to start if the schema and entities disagree, which is the intended fail-fast behavior.

## Required Environment Variables (Production)

Set these in the Render/Railway dashboard under the service's Environment settings. None of these should ever be committed to the repository.

| Variable | Description | Example |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Activates the prod profile | `prod` |
| `DB_URL` | Full JDBC connection string for the production PostgreSQL instance | `jdbc:postgresql://<host>:5432/<db>` |
| `DB_USERNAME` | Database username | — |
| `DB_PASSWORD` | Database password | — |
| `JWT_SECRET` | Base64-encoded signing secret for JWTs | Generated via PowerShell (see below) |
| `JWT_EXPIRY_MS` | Token expiry in milliseconds | `3600000` |
| `ORS_API_KEY` | OpenRouteService API key (500 req/day free tier) | — |
| `GEMINI_API_KEY` | Google Gemini API key for AI itinerary generation | — |
| `MAPBOX_TOKEN` | Mapbox public token (frontend CI injection only — not used by the backend) | `pk.eyJ1...` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed frontend origins | `https://tripflowai.app` |

**Frontend tokens:** `MAPBOX_TOKEN` is injected at CI build time via `frontend-ci.yml` (see `docs/frontend-standards.md` §Environment Files). It is a GitHub Actions secret, not a backend environment variable — the backend never uses it.

### Generating `JWT_SECRET` locally (PowerShell)

```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }))
```

Copy the output directly into the platform's environment variable settings — never into a committed file.

## Local Development Setup

1. Copy `.env.example` to `backend/.env` (gitignored) and fill in `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRY_MS`, `ORS_API_KEY`.
2. Run PostgreSQL locally on port 5432 with a `tripflow` database created.
3. In Eclipse: Run As → Spring Boot App. No `SPRING_PROFILES_ACTIVE` needed — `dev` is the default.
4. Via Maven wrapper: `.\mvnw.cmd spring-boot:run`

## CI / Test Profile

CI runs under the `test` profile with Testcontainers-provisioned PostgreSQL (no manual `DB_URL` needed — `@ImportTestcontainers` wires the connection automatically). Integration tests (`*IT.java`) only run via the `-Pci` Maven profile through Failsafe; unit tests (`*Test.java`) run via Surefire on every build.

```powershell
.\mvnw.cmd test           # unit tests only
.\mvnw.cmd verify -Pci    # unit + integration tests (requires Docker)
```

## Deployment Process

1. Merge to `main` (branch protection requires the `backend` CI status check to pass).
2. Trigger a deploy on Render/Railway (auto-deploy on push to `main`, or manual trigger from the platform dashboard).
3. Confirm environment variables are set for the target environment (see table above) — these are configured once per service, not per deploy.
4. **Verify deployment**: hit `GET /actuator/health` on the deployed URL and confirm `{"status":"UP"}`.
5. Smoke-test the auth flow: `POST /api/auth/register` → `POST /api/auth/login` → confirm a token is returned.

## Rollback Procedure

1. In the Render/Railway dashboard, locate the previous successful deploy in the deploy history.
2. Trigger "Redeploy" on that prior build — this does not require a `git revert`, since the platform retains prior build artifacts.
3. If the issue was caused by a database migration (Flyway), do **not** roll back the code without also assessing whether the migration needs a corresponding down-migration — Flyway does not auto-revert schema changes. Coordinate with the team before rolling back any change that included a new migration file.
4. Re-verify `/actuator/health` and the auth smoke test after rollback completes.

## Troubleshooting

| Symptom | Likely Cause |
|---|---|
| App fails to start with a schema validation error | Entity/migration mismatch — check for a missing Flyway migration; do not switch `ddl-auto` to fix this |
| `/actuator/health` returns 401/403 | `SecurityConfig` matcher for `/actuator/health` not applied, or CORS blocking preflight |
| CORS errors from the frontend | `CORS_ALLOWED_ORIGINS` not set or doesn't match the frontend's deployed origin exactly (no trailing slash, correct scheme) |
| Local run using wrong datasource | Confirm `SPRING_PROFILES_ACTIVE` is unset locally so `dev` default applies, or explicitly set to `dev` |