<!-- refreshed: 2026-08-06 -->
# Architecture

**Analysis Date:** 2026-08-06

## System Overview

TripFlowAI is an AI-powered multi-stop trip planning PWA with a **layered Spring Boot backend** and **standalone Angular frontend**. The architecture emphasizes clear separation of concerns: controllers handle HTTP mapping, services implement business logic, repositories abstract data access, and domain entities hold core state.

```text
┌─────────────────────────────────────────────────────────────────────┐
│                      Frontend Layer (Angular/Ionic)                  │
│  Pages (dashboard, trip-view, trip-edit) ← Components ← Services    │
│  Auth Guard  ←  Auth Service  ←  HTTP Client  ←  API Models        │
└────────────────────────────────────┬────────────────────────────────┘
                                      │
                    Bearer Token Authorization (JWT)
                                      │
┌────────────────────────────────────▼────────────────────────────────┐
│                  Backend Layer (Spring Boot 4.1, Java 21)            │
├──────────────────┬──────────────────┬────────────────────────────────┤
│   Controllers    │     Services     │  Repositories / Domain         │
│  (HTTP mapping)  │  (Business logic)│  (Data access & entities)      │
│  `controller/`   │    `service/`    │  `repository/`, `domain/`      │
├──────────────────┴──────────────────┴────────────────────────────────┤
│   DTOs & Mappers  │  Security  │  Config  │  Client  │  Exception    │
│  (Wire contracts) │ (Auth/JWT) │  (Beans) │ (ORS/AI) │  (Error hdlg) │
└────────────────────────────────────┬────────────────────────────────┘
                                      │
                 ┌─────────────────────┼──────────────┐
                 │                     │              │
                 ▼                     ▼              ▼
         ┌────────────────┐  ┌────────────────┐  ┌──────────┐
         │  PostgreSQL    │  │  External APIs │  │ Cloudinary
         │  Flyway Mgd    │  │  ORS / Gemini  │  │ (Photo)
         │  (Tripflow DB) │  │  Cloudinary    │  └──────────┘
         └────────────────┘  └────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| **Controllers** | Route HTTP requests/responses, extract JWT principal, delegate to services | `controller/{TripController, StopController, AiController, AuthController, TripExportController, StopPhotoController}` |
| **Services** | Implement business logic, orchestrate repositories, coordinate with external APIs | `service/{TripService, StopService, AuthService, RouteOptimizationService, AiItineraryService, AiTripGenerationService, TripOwnershipService, IcsExportService, StopPhotoService, OrphanPlaceCleanupJob}` |
| **Repositories** | Abstract JPA/Hibernate data access | `repository/{TripRepository, StopRepository, UserRepository, PlaceRepository, StopPhotoRepository}` |
| **Domain Entities** | Core business objects, JPA-mapped tables | `domain/{Trip, Stop, User, Place, StopPhoto, BaseEntity}` |
| **DTOs & Mappers** | Wire contract (JSON serialization), request/response shapes | `dto/` and `mapper/{TripMapper, StopMapper, AiItineraryMapper}` |
| **Security** | JWT token generation/validation, authentication filter, principal resolution | `security/{JwtService, JwtAuthFilter, UserPrincipal, SecurityConfig, JsonAuthenticationEntryPoint, JsonAccessDeniedHandler}` |
| **Client Integrations** | External API wrappers (OpenRouteService, Gemini, Cloudinary) | `client/{ors/, gemini/, cloudinary/}` |
| **Exception Handling** | Global error mapping to HTTP status codes | `exception/GlobalExceptionHandler`, custom exceptions |
| **Rate Limiting** | Token bucket limiting for AI/optimization endpoints | `ratelimit/{RateLimiterService, RateLimitConfig}` |
| **AI & Scheduling** | Prompt templates, Gemini response parsing, stop scheduling heuristics | `ai/{ItineraryPromptTemplate, TripGenerationPromptTemplate, GeminiResponseParser, SuggestedItinerary, GeneratedTripPlan}` and `schedule/{ItineraryScheduler}` |

## Pattern Overview

**Overall:** Multi-layered service-oriented architecture.

**Key Characteristics:**
- Strict layer boundaries: controllers → services → repositories → domain, enforced by `ArchitectureTest.java` at compile time
- Stateless JWT authentication (no sessions, no cookies)
- Owned-resource pattern: every trip/stop is owned by a user; access control is application-level (not Spring Security roles)
- External integrations isolated in `client/` packages with per-client exception types and configuration
- DTOs separate from domain entities; no `@Entity` in request/response objects
- Single transaction boundary per business operation (database + external API call within one `@Transactional` method where safe)

## Layers

### Controller Layer (`controller/`)

**Purpose:** Map HTTP verbs to service calls; extract JWT principal; respond with DTOs.

**Contains:**
- `TripController` — trip CRUD + optimize endpoint
- `StopController` — stop nested CRUD (within a trip)
- `AiController` — AI suggestion endpoints (`/ai-suggest`, `/ai-generate`)
- `TripExportController` — calendar ICS export
- `StopPhotoController` — photo upload/download
- `AuthController` — login/register

**Characteristics:**
- Every method parameter is a DTO (`@RequestBody`) or Spring-managed type (`@PathVariable`, `@AuthenticationPrincipal`)
- All responses are DTOs (never raw entities)
- `@Valid` ensures request validation before service entry
- JAX-RS `@Transactional` is applied at controller level only where the entire request must be atomic; most delegation to services at a finer granularity
- No repository access; all data access via services

### Service Layer (`service/`)

**Purpose:** Implement business logic, own repository access, orchestrate external APIs, enforce access control.

**Contains:**
- `TripService` — trip CRUD (create, list, get, update, delete), delegates stop-building to `StopService`
- `StopService` — stop creation, ordering, deletion, building from requests
- `RouteOptimizationService` — calls ORS VROOM, reorders stops, runs heuristic scheduler
- `AiItineraryService` — calls Gemini to suggest stops for an existing trip
- `AiTripGenerationService` — calls Gemini to generate a whole new trip + stops
- `AuthService` — login/register, password hashing, token issuance
- `TripOwnershipService` — cross-cutting concern for trip access control; used by `TripService`, `StopService`, `RouteOptimizationService`, `AiItineraryService`
- `PlaceResolutionService` — resolves place entities from create requests (deduplication, external place ID lookup)
- `StopPhotoService` — handles photo metadata persistence
- `IcsExportService` — formats stops as RFC 5545 calendar events
- `OrphanPlaceCleanupJob` — async cleanup of places no longer referenced by any stop

**Characteristics:**
- All public methods are `@Transactional` (or readonly variant) unless specifically designed as a stateless utility
- Business logic concerns (validation, authorization, data consistency) live here, not in controllers
- External API calls go through `client/` wrapper classes; service catches their exceptions and translates to domain-specific exceptions (`OrsClientException` → service logs context)
- Access control: owners checked via `TripOwnershipService.loadOwnedTrip()` or inline visibility checks (public trips readable by all)
- Logging: INFO for business operations (trip created, optimized), WARN for handled errors (403/404), DEBUG for diagnostic detail

### Repository Layer (`repository/`)

**Purpose:** JPA/Hibernate abstraction; query methods and convenience finders.

**Contains:**
- `TripRepository` — `findWithStopsById()` (collection fetch join), `findSummariesByUserId()` (projection for list endpoint)
- `StopRepository` — ordered retrieval, deletion by ID
- `UserRepository` — login lookup by email, existence check
- `PlaceRepository` — deduplication by (lat, lon), external place ID lookup
- `StopPhotoRepository` — photo retrieval by stop

**Characteristics:**
- No custom `@Query` methods; all rely on Spring Data derived queries or simple named queries
- Collection loading uses explicit `EntityGraph` or fetch joins to avoid N+1
- Read-only operations don't need `@Transactional` (Spring data method defaults handle it)
- Lazy loading used for most relationships; explicit eager loading (via repository method) only where needed to avoid LazyInitializationException

### Domain Layer (`domain/`)

**Purpose:** JPA entities and core enums; innermost layer with no outbound dependencies (except `domain/enums`).

**Contains:**
- `BaseEntity` — auto-ID, audit timestamps (`createdAt`, `updatedAt`)
- `User` — username, email (unique), password hash
- `Trip` — title, description, tags (TEXT[]), visibility (enum), status, startDate (optional), routeGeometry (JSONB), cascade-all stops
- `Stop` — name, lat/lon (via foreign key to `Place`), stopOrder, status, notes, dayNumber, plannedTime (optional, set by scheduler), stopType (SIGHTSEEING/MEAL/LODGING/OTHER)
- `Place` — name, lat/lon, address, externalPlaceId (deduplication key); shared across stops
- `StopPhoto` — URL, Cloudinary public ID, caption, timestamps
- Enums: `TripVisibility` (PRIVATE, PUBLIC), `TripStatus` (DRAFT, ...), `StopStatus` (PLANNED, VISITED, SKIPPED), `StopType` (SIGHTSEEING, MEAL, LODGING, OTHER)

**Characteristics:**
- Relationships: `Trip` ← owns → many `Stop`, `Stop` → one `Place`, `Stop` → many `StopPhoto`, `Trip` → one `User`
- Cascade rules: `Trip → Stop` is `CascadeType.ALL` with orphan removal (deleting a trip deletes its stops); `Stop → Place` is `CascadeType.MERGE` only (places outlive stops)
- No getters/setters boilerplate (Lombok `@Getter @Setter`); no business logic methods

### DTO Layer (`dto/`)

**Purpose:** Wire contract; request/response shapes. No business logic, no JPA annotations.

**Contains:**
- Requests: `CreateTripRequest`, `UpdateTripRequest`, `CreateStopRequest`, `UpdateStopRequest`, `LoginRequest`, `RegisterRequest`, `ItineraryPreferencesRequest`, `GenerateTripRequest`, `CreateStopPhotoRequest`
- Responses: `TripResponse`, `TripSummaryResponse`, `StopResponse`, `AuthResponse`, `SuggestedItineraryResponse`, `StopPhotoResponse`, `PhotoSignatureResponse`, `ApiError` (error shape)
- All use Bean Validation annotations (`@NotBlank`, `@Size`, `@DecimalMin/@DecimalMax`); validation enforced by `@Valid` at controller entry point

### Mapper Layer (`mapper/`)

**Purpose:** Translate between DTOs and domain entities.

**Contains:**
- `TripMapper` — `toEntity(CreateTripRequest, owner)`, `toResponse(Trip)`, etc.
- `StopMapper` — `toResponse(Stop)`, `toStopOrder(stops)`, etc.
- `AiItineraryMapper` — `toResponse(SuggestedItinerary)` etc.

**Characteristics:**
- Pure functions; no side effects
- Handle nulls and optional fields gracefully (e.g., `startDate` optional)
- Delegate collection transforms to callers where appropriate (to avoid N+1 in nested loops)

## Data Flow

### Primary Request Path: Trip CRUD

**Create Trip** (POST /api/trips):

1. `TripController.createTrip()` receives `CreateTripRequest` + JWT principal (`UserPrincipal`)
2. Controller validates request via `@Valid` (Bean Validation)
3. Controller delegates to `TripService.createTrip(userId, request)`
4. Service builds entities:
   - `TripMapper.toEntity(request, owner)` creates `Trip` with metadata
   - `StopService.buildStops(request.stops, trip)` resolves each stop's place (via `PlaceResolutionService.resolvePlace()`) and creates `Stop` entities in order
5. `TripRepository.save(trip)` persists trip + stops in cascade (one INSERT per entity)
6. Service logs INFO: "Trip created id={} ownerId={} stops={}"
7. Controller responds with `TripResponse` (mapped from persisted trip)

**Get Trip** (GET /api/trips/{id}):

1. `TripController.getTrip(id, principal)` extracts JWT user ID
2. `TripService.getTrip(id, requesterId)`:
   - `TripRepository.findWithStopsById(id)` eager-loads stops via fetch join (avoids N+1)
   - Checks visibility: if PRIVATE and requester is not owner → 403 ForbiddenException
   - Returns `TripResponse`
3. Controller sends 200 + response DTO

**Update Trip** (PUT /api/trips/{id}):

1. Controller extracts `UpdateTripRequest` + principal
2. `TripService.updateTrip(id, userId, request)`:
   - `TripOwnershipService.loadOwnedTrip(id, userId)` ensures owner-only access (or 403 ForbiddenException)
   - Maps new stops via `TripMapper.toEntity()` + `StopService.buildStops()`
   - Existing stops not in request are removed (cascade orphan deletion)
   - `TripRepository.save(trip)` persists with new stops
3. Controller responds with updated `TripResponse`

**Delete Trip** (DELETE /api/trips/{id}):

1. Controller extracts principal
2. `TripService.deleteTrip(id, userId)` checks ownership
3. `TripRepository.delete(trip)` cascades to stops
4. Controller responds 204 No Content

### Secondary Flow: Route Optimization

**Optimize Trip** (POST /api/trips/{id}/optimize):

1. Controller checks rate limit: `RateLimiterService.checkLimit("optimize:" + userId, ...)`
2. `RouteOptimizationService.optimize(id, userId)`:
   - `TripOwnershipService.loadOwnedTrip()` ensures owner + trip exists
   - Validates ≥2 stops; raises 422 InsufficientStopsException if not
   - Builds `OrsOptimizationRequest` from current stops (lat/lon from `Place`)
   - Calls `OrsClient.optimize()` via HTTP; receives `OrsOptimizationRequest` with optimized order + route geometry + leg times
   - If ORS returns error → catch `OrsClientException`, let it propagate (GlobalExceptionHandler maps to 502)
   - Reorders `Stop` entities by ORS output order
   - Calls `ItineraryScheduler.schedule()` to assign `dayNumber` + `plannedTime` to each stop using per-leg travel times
   - Persists trip + geometry via `TripRepository.save()`
3. Controller responds with `TripResponse` (full trip with reordered stops)

### Tertiary Flow: AI Itinerary Suggestions

**Suggest Itinerary** (POST /api/trips/{id}/ai-suggest):

1. Controller extracts `ItineraryPreferencesRequest` (interests, budget, pace) + principal
2. Validates request size limits (interests ≤10 × 50 chars, etc.)
3. Rate limit check: `RateLimiterService.checkLimit("ai-suggest:" + userId, ...)`
4. `AiItineraryService.suggest(id, userId, preferences)`:
   - `TripOwnershipService.loadOwnedTrip()` ensures owner
   - Builds `ItineraryPromptTemplate` from trip + preferences
   - Calls `GeminiClient.generateContent()` with prompt
   - Parses `GeminiGenerateContentResponse`; extracts structured itinerary via `GeminiResponseParser`
   - Returns `SuggestedItinerary` (NOT persisted; frontend POSTs individual stops via addStop if accepted)
   - If Gemini fails → catch `GeminiClientException` or `GeminiParsingException`, let propagate (502)
5. Controller responds with `SuggestedItineraryResponse`

**Generate Trip with AI** (POST /api/trips/ai-generate):

1. Controller extracts `GenerateTripRequest` (prompt, optional title) + principal
2. Rate limit check: `RateLimiterService.checkLimit("ai-generate:" + userId, ...)`
3. `AiTripGenerationService.generate(userId, request)`:
   - Builds `TripGenerationPromptTemplate` from free-text prompt
   - Calls `GeminiClient.generateContent()`
   - Parses response into `GeneratedTripPlan` (title + list of suggested stops)
   - Creates new `Trip` (owned by caller, PRIVATE visibility) + persists stops
   - Returns `TripResponse` (201 Created)
4. Same error handling as above (502 on Gemini failure, 429 on rate limit)

### Authentication Flow

1. Client calls POST /api/auth/login with `LoginRequest` (email, password)
2. `AuthController.login()` → `AuthService.login(email, password)`:
   - `UserRepository.findByEmail(email)` retrieves user
   - BCrypt `passwordEncoder.matches(rawPassword, user.passwordHash)` validates
   - If mismatch → InvalidCredentialsException → 401 via GlobalExceptionHandler
   - `JwtService.generateToken(user)` issues HMAC-SHA256 signed token (sub=userId, email claim)
   - Returns `AuthResponse` with token + expiresAt
3. Client stores token (localStorage) and sends `Authorization: Bearer <token>` on subsequent requests
4. `JwtAuthFilter` (per-request, `OncePerRequestFilter`):
   - Extracts token from header
   - `JwtService.parseIfValid(token)` validates signature, expiry, claims
   - On success: constructs `UserPrincipal(userId, email)` and sets `SecurityContext`
   - On failure (expired, invalid sig): logs WARN, allows request to proceed (will be rejected at controller entry if protected)
5. Controllers receive JWT principal via `@AuthenticationPrincipal UserPrincipal principal`
6. Routes: `SecurityConfig` denies all except `/api/auth/**` and `/actuator/health` → any new controller method is automatically protected

### Error Handling Path

All exceptions propagate to `GlobalExceptionHandler`:

| Exception | HTTP Status | Response Body |
|-----------|-------------|---|
| `ResourceNotFoundException` | 404 | `ApiError` with message |
| `ForbiddenException` | 403 | `ApiError` with message |
| `InvalidCredentialsException` | 401 | `ApiError` with generic message ("Invalid email or password") |
| `DuplicateEmailException` / `DuplicateUsernameException` | 409 | `ApiError` with message |
| `InsufficientStopsException` | 422 | `ApiError` with message |
| `OrsClientException` | 502 | `ApiError` with "Route service is temporarily unavailable" |
| `GeminiClientException` | 502 | `ApiError` with "AI itinerary service is temporarily unavailable" |
| `GeminiParsingException` | 502 | `ApiError` with "AI itinerary service returned an unreadable response" |
| `OrsRateLimitException` | 429 | `ApiError` with message (no Retry-After) |
| `RateLimitExceededException` | 429 | `ApiError` with message + Retry-After header |
| `MethodArgumentNotValidException` (Bean Validation) | 400 | `ApiError` with message + `fieldErrors` array |
| `HttpMessageNotReadableException` (malformed JSON) | 400 | `ApiError` with "Malformed request body" (don't echo parse error) |
| `MethodArgumentTypeMismatchException` (path var type mismatch) | 400 | `ApiError` with "Invalid value for parameter" |

**Response Shape (all errors):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "string",
  "path": "/api/trips/5",
  "timestamp": "2026-08-06T12:34:56.789Z",
  "fieldErrors": [
    { "field": "title", "message": "must not be blank" }
  ]
}
```

## Key Abstractions

### Owned Resource Pattern

Every trip is owned by exactly one user. Access control is **application-level**, not Spring Security roles.

**Abstraction:** `TripOwnershipService`

- `loadOwnedTrip(tripId, userId)` — fetch a trip the caller must own, or throw 403
- Used by update/delete/optimize operations
- Exception: `GET /api/trips/{id}` allows non-owners to see PUBLIC trips (custom visibility check, doesn't use this service)

### External API Client Pattern

Each external service (ORS, Gemini, Cloudinary) is wrapped in a dedicated package under `client/`:

```
client/ors/
  ├── OrsClient (HTTP wrapper, exception translation)
  ├── OrsProperties (@ConfigurationProperties, secrets masked in toString())
  ├── OrsClientConfig (RestTemplate bean, timeouts)
  ├── OrsDirectionsRequest / OrsDirectionsResponse (wire DTOs)
  ├── OrsOptimizationRequest / OrsOptimizationResponse (wire DTOs)
  └── OrsClientException (caught by service, translated to 502)
```

**Pattern:**
- `@ConfigurationProperties` per client for externalized config (API key, timeout, URL)
- Wire-format DTOs (records) separate from domain
- Per-client exception types (`OrsClientException`, `GeminiClientException`)
- Client exceptions always translated to 502 by GlobalExceptionHandler (never let raw HTTP failure escape to frontend)
- Client classes injected into services; never accessed from controllers

### Rate Limiting via Token Bucket

`RateLimiterService` uses Bucket4j (in-memory token bucket) keyed on JWT user ID.

**Endpoints limited:**
- `POST /api/trips/{id}/optimize` — 20/hour (externalized in application.properties)
- `POST /api/trips/{id}/ai-suggest` — 10/hour
- `POST /api/trips/ai-generate` — 5/hour

**Implementation:**
- `RateLimiterService.checkLimit(key, capacity, window)` throws `RateLimitExceededException` if bucket exhausted
- Exception caught by GlobalExceptionHandler → 429 + `Retry-After` header
- Per-user (JWT sub), not per-IP (for shared networks)
- Single-instance in-memory; resets on restart (no distributed backend needed yet)

### Stop Scheduling Heuristic

`ItineraryScheduler` assigns `dayNumber` + `plannedTime` to each stop after route optimization.

**Algorithm:**
1. For each optimized stop (in order):
   - Assume `app.schedule.default-visit-duration` (default 1h) to visit
   - Add leg travel time from ORS directions call to cumulative time
   - If cumulative time exceeds day end (default 21:00) → increment dayNumber, reset cumulative time to start of next day
   - Assign `dayNumber` and `plannedTime` to stop
2. Result: trip is decomposed into multi-day itinerary without Gemini involvement
3. Foundation for future AI-driven scheduling (SCRUM-244b+)

### Heuristic Scheduler

The `RouteOptimizationService` uses two external integrations in sequence:

1. **OpenRouteService (VROOM)** — reorders stops for TSP (traveling salesman), returns optimized order + route geometry + per-leg travel times
2. **ItineraryScheduler** — uses per-leg times to greedily assign stops to days and times

No persistent state between calls; each optimize is independent. Geometry stored as encoded polyline in `trip.routeGeometry` (JSONB).

### Place Deduplication

`PlaceResolutionService.resolvePlace(createStopRequest)` deduplicates places:

- If request has `externalPlaceId` (from Mapbox), look up existing place by that ID
- Else if same (lat, lon) exists, reuse that place
- Else insert new place

Multiple stops can share one `Place` row if they map to the same coordinates or external ID.

## Entry Points

### Backend Entry Point

**Application:** `BackendApplication.java`

- Standard Spring Boot `@SpringBootApplication` with main method
- Auto-scans `com.tripflow.backend.*` packages
- Flyway migrations run on startup (schema validation mode)

**Actuator Endpoints** (health, metrics):
- `/actuator/health` — permitted without auth (readiness check for load balancer)
- `/actuator/metrics` — permitted without auth (Prometheus scrape)
- Swagger UI at `/swagger-ui.html`, `/api-docs` (permitted)

**Server Properties:**
- Port 8080 (default)
- Datasource: PostgreSQL (from environment variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_PASSWORD`)

### Frontend Entry Point

**Application:** `app.routes.ts`

- Standalone routing config (no NgModules)
- Protected routes use `authGuard` (checks JWT expiry before each navigation)
- Routes:
  - `''` → redirect to login
  - `login`, `signup` → unprotected auth pages
  - `starting-up` → loading screen for backend startup (SCRUM-273)
  - `dashboard` → trip list (protected)
  - `trips/new`, `trips/{id}`, `trips/{id}/edit` → trip CRUD pages (protected)

**HTTP Client Setup:**
- `HttpClient` configured with interceptors in `core/http/`
- Base URL from `environment.apiBaseUrl` (environment.ts/environment.prod.ts)
- Token sent as `Authorization: Bearer <token>` on all requests via interceptor
- Error handling via `mapApiError()` (HTTP error status → domain error object)

## Architectural Constraints

- **Threading:** Spring Boot request threads (servlet container); event loop in frontend (RxJS)
- **Global state:** `TripService.trips` signal in frontend (singleton, shared across pages); stateless backend (no servlet session, JWT only)
- **Circular imports:** None known; layering enforced by `ArchitectureTest`
- **JPA Lazy Loading:** Several relationships use `FetchType.LAZY` to avoid loading unnecessary data; explicit eager loading (fetch joins) only where collection is accessed in response
- **Database Lock Contention:** Not a concern at current scale (no concurrent updates to same trip); if multi-user editing comes later, optimistic locking (version field) should be added
- **API Rate Limiting:** Per-user token bucket (in-memory, not distributed); suitable for single instance; multi-instance deployment requires Redis backend

## Anti-Patterns

### Bypassing the Service Layer

**What happens:** A controller reaches directly into a repository (e.g., `tripRepository.findById()` without going through `TripService`).

**Why it's wrong:** Business logic (access control, validation, transaction coordination) lives in services. Controllers directly accessing repositories duplicates logic and breaks the layer boundary that `ArchitectureTest` enforces.

**Do this instead:** Add a service method that encapsulates the query + business logic. All repository access from services only. See `TripService.getTrip()` for the pattern: service handles visibility check before returning to controller.

### Placing JPA Entities in DTOs

**What happens:** A `Trip` entity is returned from a service directly to a REST controller, which serializes it as JSON.

**Why it's wrong:** Entities couple the wire contract to the database schema. Schema changes force API changes. Lazy-loaded collections serialize to null or cause N+1 queries.

**Do this instead:** Always map entities to DTOs before returning from service. Use mappers: `tripMapper.toResponse(trip)`. DTOs are the API contract; entities are internal.

### External API Exceptions Escaping to HTTP

**What happens:** `OrsClient.optimize()` throws an uncaught exception (e.g., `SocketTimeoutException`) that Spring maps to 500 Internal Server Error, exposing infrastructure details.

**Why it's wrong:** Clients can't distinguish between our bugs and infrastructure transience. No retry guidance.

**Do this instead:** Wrap external HTTP calls in try-catch at the client level, translate to a domain exception (`OrsClientException`). Let `GlobalExceptionHandler` map it to 502 Bad Gateway. See `OrsClient` and `GlobalExceptionHandler.handleOrsFailure()`.

### Storing Secrets in Code/Commits

**What happens:** API key or JWT secret is committed in `application.properties` or `.env` file.

**Why it's wrong:** Secrets in git history are visible to all users with repo access; rotation is cumbersome.

**Do this instead:** Read from environment variables at runtime. Backend expects `DB_PASSWORD`, `JWT_SECRET`, `ORS_API_KEY`, `GEMINI_API_KEY` in the `.env` file (not committed, developer-local). See `JwtProperties`, `OrsProperties`, `GeminiProperties` for the pattern: `@ConfigurationProperties` reading from `application.properties` which interpolates from env vars.

## Error Handling

**Strategy:** Fail fast with clear error messages; never let implementation details leak to the client; log enough server-side to debug.

**Patterns:**
- **400 Bad Request:** Validation failures (MethodArgumentNotValidException, field-level constraints), malformed JSON, invalid path parameters. Include `fieldErrors` array for validation so client can highlight form fields.
- **401 Unauthorized:** Missing, expired, or invalid JWT. Sent by `JsonAuthenticationEntryPoint` (not GlobalExceptionHandler); client should redirect to login.
- **403 Forbidden:** Valid JWT but the operation is not permitted (not trip owner, private trip). Sent by GlobalExceptionHandler.
- **404 Not Found:** Trip/stop/user doesn't exist or is not accessible.
- **409 Conflict:** Email/username already registered; a resource constraint violation.
- **422 Unprocessable Entity:** Trip has fewer than 2 stops (can't optimize); or Gemini returned zero stops (can't create trip).
- **429 Too Many Requests:** Rate limit exceeded; includes `Retry-After` header (seconds until next token available).
- **502 Bad Gateway:** External API (ORS, Gemini, Cloudinary) is unreachable or returned an error. No retry guidance (not the client's fault); should show generic message.

## Cross-Cutting Concerns

**Logging:**
- Framework: SLF4J via Lombok `@Slf4j`
- Levels: ERROR for unhandled exceptions (with throwable), WARN for handled 4xx/auth failures, INFO for business operations (trip created), DEBUG for diagnostic detail
- Never log passwords, JWTs, Authorization headers, API keys, PII bodies
- Use parameterized messages: `log.info("Trip created id={} ownerId={}", id, userId)` (not string concatenation)
- See `docs/LOGGING_STANDARD.md` for full rules

**Validation:**
- Frontend: template-driven (Ionic form validators)
- Backend: Bean Validation annotations on DTOs (`@NotBlank`, `@Size`, `@DecimalMin/@DecimalMax`)
- Enforced by `@Valid` at controller entry point; errors converted to 400 with `fieldErrors`
- Field limits documented in `docs/api-contracts.md` and mirrored in DB column widths

**Authentication:**
- Stateless JWT via Spring Security
- No sessions, no cookies
- `JwtAuthFilter` validates token per-request
- `UserPrincipal` resolved in controllers via `@AuthenticationPrincipal`
- See `docs/auth.md` for full auth flow and 401 vs 403 distinction

---

*Architecture analysis: 2026-08-06*
