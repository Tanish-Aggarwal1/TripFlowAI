# External Integrations

**Analysis Date:** 2026-08-06

## APIs & External Services

**Route Optimization:**
- OpenRouteService (ORS) - VROOM matrix/directions/optimization API
  - SDK/Client: `backend/src/main/java/com/tripflow/backend/client/ors/`
    - `OrsClient` - Main client wrapper
    - `OrsClientConfig` - RestClient bean configuration (per-service timeouts)
    - `OrsProperties` - Configuration properties record with `@ConfigurationProperties("ors")`
    - Request DTOs: `OrsDirectionsRequest`, `OrsOptimizationRequest`
    - Response DTOs: `OrsDirectionsResponse`, `OrsOptimizationResponse`
  - Base URL: `https://api.openrouteservice.org`
  - Auth: API key via `Authorization` header, loaded from `ORS_API_KEY` env var
  - Timeouts: Connect 5s, Read 15s (configurable via `ors.connect-timeout`, `ors.read-timeout`)
  - Rate limit: 500 requests/day on free tier (enforced at app level for premium tiers via Bucket4j)
  - Exposed by: `RouteOptimizationService` (`backend/src/main/java/com/tripflow/backend/service/`)
  - Error handling: `OrsClientException` → translated to 502 by `GlobalExceptionHandler`

**AI Content Generation:**
- Google Gemini API - Itinerary suggestions and AI-powered trip planning
  - SDK/Client: `backend/src/main/java/com/tripflow/backend/client/gemini/`
    - `GeminiClient` - Main client wrapper
    - `GeminiClientConfig` - RestClient bean configuration
    - `GeminiProperties` - Configuration properties record with `@ConfigurationProperties("app.gemini")`, secrets masked in `toString()`
    - Request DTO: `GeminiGenerateContentRequest`
    - Response DTO: `GeminiGenerateContentResponse`
  - Base URL: `https://generativelanguage.googleapis.com`
  - Model: `gemini-flash-lite-latest` (configurable via `app.gemini.model`)
  - Auth: API key via URL parameter (per Gemini API), loaded from `GEMINI_API_KEY` env var
  - Timeouts: Connect 5s, Read 30s (longer read for AI inference)
  - Exposed by: `AiSuggestionService` (`backend/src/main/java/com/tripflow/backend/ai/`)
  - Error handling: `GeminiClientException` → translated to 502 by `GlobalExceptionHandler`
  - Rate limits: 10 requests/hour for ai-suggest, 5 requests/hour for ai-generate (Bucket4j)

**Maps & Geospatial:**
- Mapbox GL JS - Frontend map rendering and interactive visualization
  - Frontend import: `mapbox-gl` 3.27.0 (`frontend/package.json`)
  - TypeScript types: `@types/mapbox-gl` 3.5.0
  - Token: `__MAPBOX_TOKEN__` placeholder in `environment.ts` / `environment.prod.ts`
  - CI injection: Mapbox token injected at build time via `sed` in `frontend-ci.yml` (MAPBOX_TOKEN secret)
  - Used in: Place picker, trip route visualization, stop markers

**Image Storage & CDN:**
- Cloudinary - User photo storage and URL generation
  - SDK/Client: `backend/src/main/java/com/tripflow/backend/client/cloudinary/`
    - `CloudinaryProperties` - Configuration properties record with `@ConfigurationProperties("app.cloudinary")`, secrets masked in `toString()`
    - `CloudinarySigningService` - Signed upload request generation
    - DTO: `SignedUploadRequest`
  - Auth: API key + API secret (never exposed to frontend)
  - Credentials loaded from: `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` env vars
  - Dev placeholders: `app.cloudinary.cloud-name=dev-cloud-placeholder`, etc.
  - Exposed by: `PhotoService` (frontend uploads via signed requests, never hardcoded credentials)

## Data Storage

**Databases:**
- PostgreSQL 16 (required for local development per `CLAUDE.md`)
  - Connection: `jdbc:postgresql://localhost:5432/tripflow` (dev), environment-driven (prod via `DB_URL`)
  - Credentials: `DB_USERNAME`, `DB_PASSWORD` from `.env` or environment variables
  - Client: PostgreSQL JDBC Driver (runtime scope in pom.xml)
  - ORM: Hibernate via Spring Data JPA
  - Managed by: Flyway (database migrations)

**Migrations:**
- Flyway 10 (PostgreSQL-specific)
  - Location: `backend/src/main/resources/db/migration/`
  - Current migrations:
    - V1__create_users.sql - User accounts and authentication
    - V2__create_trips.sql - Trip planning
    - V3__create_places.sql - Waypoint/destination data
    - V4__create_stops.sql - Trip stops
    - V5__cleanup_orphan_places.sql - Data cleanup
    - V6__stops_unique_trip_stop_order.sql - Unique constraint on stop ordering
    - V7__stop_scheduling.sql - Scheduling/timing data
    - V8__create_stop_photos.sql - Photo associations
  - Validation only: `spring.jpa.hibernate.ddl-auto=validate` in all profiles — Flyway is the single source of truth

**File Storage:**
- Local filesystem (development)
- Cloudinary (production image uploads)

**Caching:**
- In-memory rate limiting via Bucket4j (per-user, per-endpoint buckets)

## Authentication & Identity

**Auth Provider:**
- Stateless JWT + Spring Security (no sessions)
  - Implementation: `JwtAuthFilter` (OncePerRequestFilter) in `backend/src/main/java/com/tripflow/backend/security/`
  - Token validation: Per-request via `JwtService`
  - Principal: `UserPrincipal implements UserDetails` (real Spring Security principal)
  - Configuration: `JwtConfig` + `JwtProperties` in `backend/src/main/java/com/tripflow/backend/security/`

**JWT Configuration:**
- Secret: `JWT_SECRET` env var (never in application properties)
- Expiry: `JWT_EXPIRY_MS` env var, default 3600000 (1 hour)
- Library: JJWT 0.13.0 (jjwt-api, jjwt-impl, jjwt-jackson)

**Password Hashing:**
- BCrypt (via Spring Security)

**Entry Points & Error Handling:**
- `JsonAuthenticationEntryPoint` - 401 Unauthorized (missing/invalid token)
- `JsonAccessDeniedHandler` - 403 Forbidden (insufficient permissions)
- `ForbiddenException` - Application-level 403 (ownership checks, authorization)
- All security errors return `ApiError` JSON (status, error, message, path, timestamp, fieldErrors if applicable)

## Monitoring & Observability

**Error Tracking:**
- None detected (no Sentry, DataDog, etc.)

**Logs:**
- SLF4J via Lombok `@Slf4j`
- Development: `root=INFO`, `com.tripflow.backend=DEBUG`, `show-sql=true`
- Production: `root=INFO`, `com.tripflow.backend=INFO`, `show-sql=false`
- Levels: `ERROR` (unhandled exceptions with throwable), `WARN` (handled 4xx/auth failures), `INFO` (one-line business audit), `DEBUG` (diagnostic detail)
- Parameterized messages only (no string concatenation); never log passwords, JWTs, Authorization headers, API keys, or PII bodies
- Full logging standard: `docs/LOGGING_STANDARD.md`

**Health Checks:**
- Spring Boot Actuator: `/actuator/health` (no auth required, returns `{"status":"UP"}`)
- Exposed endpoints: `health`, `metrics` only (dev: all; prod: health/metrics)
- Disabled endpoints: `env`, `beans`, `heapdump`, `threaddump`, `configprops`

**Metrics:**
- Spring Boot built-in metrics endpoint: `/actuator/metrics`

## CI/CD & Deployment

**Hosting:**
- Not detected in codebase (configuration exists for Render via `PORT` env var and environment-driven secrets)

**CI Pipeline:**
- GitHub Actions
  - `backend-ci.yml`: Runs `mvn -B verify -Pci` on PRs and push to main; JaCoCo coverage check (92% overall / 80% changed-files); publishes coverage report artifact; posts PR comment
  - `frontend-ci.yml`: Path-scoped to `frontend/**`; runs linting, testing, build; injects Mapbox token from secret; injects optional API base URL; publishes coverage report
  - Both workflows have concurrency control (cancel-in-progress)
  - Integration tests (`*IT.java`) run only in CI via `-Pci` Maven profile (Docker required)
  - Unit tests run locally via `mvn verify` (no Docker)

**Build Process:**
- Backend: `mvn spring-boot:run` (dev), `mvn verify` (test), `mvn verify -Pci` (full CI with Docker integration tests)
- Frontend: `npm start` (ionic serve), `npm run build`, `npm run test:ci`

**Deployment Readiness:**
- Health check endpoint: `GET /actuator/health`
- All secrets injected via environment variables (never in code/config files)
- CORS: Environment-driven `app.cors.allowed-origins` in prod
- Swagger UI: Disabled in production (`springdoc.swagger-ui.enabled=false`)

## Environment Configuration

**Required env vars:**
- `DB_USERNAME`, `DB_PASSWORD` - PostgreSQL credentials
- `DB_URL` - PostgreSQL connection string (prod only)
- `JWT_SECRET` - Secret for JWT signing
- `JWT_EXPIRY_MS` - JWT expiry in milliseconds (optional, defaults to 3600000)
- `ORS_API_KEY` - OpenRouteService API key
- `GEMINI_API_KEY` - Google Gemini API key
- `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` - Image storage
- `MAPBOX_TOKEN` - Frontend map token (injected at build time, not runtime)
- `CORS_ALLOWED_ORIGINS` - Comma-separated CORS origin list (prod)
- `PORT` - Server port (optional, defaults to 8080)

**Secrets location:**
- Backend: `.env` file (backend/.env, `.gitignore`d, never committed)
- Frontend: GitHub Actions secrets (`MAPBOX_TOKEN`, `API_BASE_URL` optional)
- CI variables: Injected via `sed` substitution in GitHub Actions workflows

## Webhooks & Callbacks

**Incoming:**
- None detected

**Outgoing:**
- None detected

---

*Integration audit: 2026-08-06*
