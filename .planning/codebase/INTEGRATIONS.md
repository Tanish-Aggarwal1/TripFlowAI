# External Integrations

**Analysis Date:** 2026-08-14

## APIs & External Services

**Route Optimization & Directions:**
- OpenRouteService (VROOM) - Multi-stop route optimization and directions
  - SDK/Client: Custom wrapper via `backend/src/main/java/com/tripflow/backend/client/ors/OrsClient.java`
  - Config: `OrsProperties` record in `backend/src/main/java/com/tripflow/backend/client/ors/OrsProperties.java`
  - Auth: API key via `ORS_API_KEY` env var (free tier: 500 req/day)
  - Base URL: `https://api.openrouteservice.org`
  - Timeouts: Connect 5s, Read 15s
  - Endpoints used:
    - `/v2/directions/{profile}/geojson` - Get turn-by-turn directions (POST)
    - `/optimization` - VROOM route optimization (POST)
  - Exception handling: `OrsClientException` (502), `OrsRateLimitException` (429)
  - Configured in: `application.properties` (ors.*)

**AI Suggestions & Content Generation:**
- Google Gemini - AI-powered trip suggestions and itinerary generation
  - SDK/Client: Custom wrapper via `backend/src/main/java/com/tripflow/backend/client/gemini/GeminiClient.java`
  - Config: `GeminiProperties` record in `backend/src/main/java/com/tripflow/backend/client/gemini/GeminiProperties.java`
  - Auth: API key via `GEMINI_API_KEY` env var (sent as request header)
  - Base URL: `https://generativelanguage.googleapis.com`
  - Model: `gemini-flash-lite-latest`
  - Timeouts: Connect 5s, Read 30s
  - Endpoint: `/v1beta/models/{model}:generateContent` (POST)
  - Exception handling: `GeminiClientException` (502)
  - Configured in: `application.properties` (app.gemini.*)
  - Rate limited: 10 req/hour per user (ai-suggest), 5 req/hour per user (ai-generate)

**Maps & Geospatial:**
- Mapbox GL - Vector mapping, interactive maps, geocoding visualization
  - SDK/Client: NPM package `mapbox-gl@3.28.1` in `frontend/package.json`
  - Auth: Mapbox API token via `MAPBOX_TOKEN` repo secret
  - Config: Injected at build time into `frontend/src/environments/environment.prod.ts` via `sed` substitution in CI
  - Used in: Frontend map components for trip visualization and stop placement
  - Token placement: GitHub Actions secret `MAPBOX_TOKEN` (substituted before frontend build in `frontend-ci.yml`)

**Image & Asset Storage:**
- Cloudinary - Image upload and CDN for stop/trip photos
  - SDK/Client: HTTP-based integration via `backend/src/main/java/com/tripflow/backend/client/cloudinary/`
  - Config: `CloudinaryProperties` record (`CloudinaryProperties.java`)
  - Auth: Cloud name, API key, API secret via `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` env vars
  - Configuration: `application.properties` (app.cloudinary.*)
  - Used for: Stop photo upload and serving via CDN

## Data Storage

**Databases:**
- PostgreSQL 16 (primary transactional database)
  - Connection: Via `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password` env vars
  - Driver: `org.postgresql.Driver` (PostgreSQL JDBC driver)
  - Client: Spring Data JPA with Hibernate ORM
  - DDL: Flyway migrations (11 migrations in `backend/src/main/resources/db/migration/V*__*.sql`)
  - Validation: `spring.jpa.hibernate.ddl-auto=validate` (schema-first, never auto-generates)
  - Connection Pool: Hikari (Spring default), max pool size: 5 in prod
  - Lazy loading: Disabled (`spring.jpa.open-in-view=false`) to prevent connection pool exhaustion
  - Development: Local PostgreSQL 16 with `tripflow` database
  - Production: Cloud-hosted (likely Render.com or similar PaaS)

**File Storage:**
- Local filesystem only for development/testing
- Cloudinary for production image assets (see above)

**Caching:**
- In-memory rate limiting only (Bucket4j per-user token buckets)
- No Redis or external cache layer currently deployed

## Authentication & Identity

**Auth Provider:**
- Custom stateless JWT (JSON Web Tokens)
  - Implementation: JJWT 0.13.0 library (`io.jsonwebtoken:jjwt-*`)
  - Filter: `JwtAuthFilter` (`OncePerRequestFilter`) validates token per-request
  - User Principal: `UserPrincipal implements UserDetails` (set on `SecurityContext`, not a raw ID)
  - Configuration: `JwtProperties` record and `JwtConfig`/`JwtAuthFilter` in `backend/src/main/java/com/tripflow/backend/security/`
  - Token Secret: `JWT_SECRET` env var (HMAC-SHA256)
  - Token Expiry: `JWT_EXPIRY_MS` env var (default 1 hour)
  - Access: Via `@AuthenticationPrincipal UserPrincipal principal` in controllers

**Authorization:**
- Spring Security stateless configuration (no sessions)
- Method-level authorization via Spring annotations
- Ownership checks: Application-level (returns 403 ForbiddenException), not Spring Security
- Path-level: `permitAll` set includes `/api/auth/**`, `/api/discovery/**`, `/actuator/health`, `/actuator/metrics`, `/swagger-ui**`, `/api-docs**` (swagger disabled in prod)
- Rate limiting on auth endpoints: Login 10 req/hour per IP, Register 5 req/hour per IP (bucket4j)

**Error Responses:**
- 401 Unauthorized: `JsonAuthenticationEntryPoint` (missing/invalid token)
- 403 Forbidden: `JsonAccessDeniedHandler` (authenticated but lacks permission) or `ForbiddenException` (app-level ownership check)

## Monitoring & Observability

**Error Tracking:**
- Not currently integrated (no Sentry, Rollbar, etc.)
- Errors logged locally via SLF4J
- No remote error tracking in production

**Logs:**
- Spring Boot logging via SLF4J
- Log level: INFO for root in prod, DEBUG for `com.tripflow.backend` in dev
- No external log aggregation (Datadog, ELK, etc.)
- Parameterized messages only (no string concatenation)
- Never logged: passwords, JWTs, Authorization headers, API keys, PII bodies
- Full logging rules in `docs/LOGGING_STANDARD.md`

**Health & Metrics:**
- Spring Boot Actuator (management.endpoints.web.exposure)
- Health endpoint: `/actuator/health` (publicly exposed, shows-details=never)
- Metrics endpoint: `/actuator/metrics` and `/actuator/metrics/**` (publicly exposed, SCRUM-174)
- Disabled endpoints: `env`, `beans`, `heapdump`, `threaddump`, `configprops` (explicit disable in all profiles)

## CI/CD & Deployment

**Hosting:**
- Render.com (backend REST API)
  - Auto-deploys from `main` branch via git integration
  - Environment: Node.js buildpack or Docker (backend uses Docker image)
  - Port binding: Reads `PORT` env var, defaults to 8080
  - Proxy handling: X-Forwarded headers (RemoteIpValve, CF-Connecting-IP for Cloudflare)

- Render.com or similar (frontend PWA)
  - Deployed separately, serves static assets (`frontend/dist/app/`)
  - API base URL injected at build time

**CI Pipeline:**
- GitHub Actions
  - `.github/workflows/backend-ci.yml` - Maven `verify -Pci` (unit + integration tests via Testcontainers)
  - `.github/workflows/frontend-ci.yml` - npm test:ci, lint, audit
  - `.github/workflows/pr-title-check.yml` - Enforces PR title format `[SCRUM-XXX] type(scope): message`
  - `.github/workflows/codeql.yml` - CodeQL security scanning

**Testing in CI:**
- Backend: `mvn -B verify -Pci` (includes `*IT.java` integration tests with Testcontainers PostgreSQL)
- Frontend: `npm run test:ci` (Karma headless Chrome), `npm run lint`, `npm audit`
- Coverage reporting: JaCoCo (backend, min 92% overall / 80% changed files), Karma (frontend)

**Deployment Artifact:**
- Backend: Docker image (multi-stage build, `backend/Dockerfile`)
- Frontend: Static site (Angular build output)

## Environment Configuration

**Required env vars (Backend):**
- `JWT_SECRET` - HMAC-SHA256 key for JWT signing (no default)
- `JWT_EXPIRY_MS` - Token lifetime in ms (default 3600000 = 1 hour)
- `ORS_API_KEY` - OpenRouteService API key (no default)
- `GEMINI_API_KEY` - Google Gemini API key (no default)
- `CLOUDINARY_CLOUD_NAME` - Cloudinary account ID (no default)
- `CLOUDINARY_API_KEY` - Cloudinary API key (no default)
- `CLOUDINARY_API_SECRET` - Cloudinary API secret (no default)

**Prod-only env vars:**
- `DB_URL` - PostgreSQL connection URL
- `DB_USERNAME` - PostgreSQL username
- `DB_PASSWORD` - PostgreSQL password
- `CORS_ALLOWED_ORIGINS` - Comma-separated list of allowed CORS origins
- `API_BASE_URL` - (Optional, frontend build time) Backend API URL for prod frontend
- `MAPBOX_TOKEN` - (GitHub Actions secret, injected at frontend build time)

**Dev-only env vars:**
- Same required vars, but dev profile uses local PostgreSQL connection string

**Secrets location:**
- Backend: `.env` file (gitignored, copied from `.env.example`)
- Frontend: GitHub Actions secrets (`MAPBOX_TOKEN`, `API_BASE_URL`)

## Webhooks & Callbacks

**Incoming:**
- None currently (API is REST query-only)

**Outgoing:**
- None currently (Render auto-deployment is unidirectional — GitHub → Render)

## Rate Limiting

**Per-User (Authenticated):**
- AI Suggest: 10 req/hour (Bucket4j in-memory)
- Route Optimization: 20 req/hour
- AI Generate: 5 req/hour (lower; also persists trip)

**Per-IP (Unauthenticated Auth Endpoints):**
- Login: 10 req/hour (mitigates brute force)
- Register: 5 req/hour (mitigates automated account creation)

**External API Quotas:**
- OpenRouteService: 500 req/day (free tier, shared across both `/directions` and `/optimization`)
- Google Gemini: Depends on billing tier (rate-limited server-side)

---

*Integration audit: 2026-08-14*
