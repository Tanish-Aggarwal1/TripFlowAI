<!-- refreshed: 2026-08-14 -->
# Architecture

**Analysis Date:** 2026-08-14

## System Overview

TripFlowAI is a multi-stop trip planning PWA combining a layered Spring Boot backend (Java 21) with a standalone Angular frontend (Ionic 8). The system orchestrates three external services: OpenRouteService (VROOM) for route optimization, Google Gemini for AI itinerary generation, and Cloudinary for photo storage.

```text
┌─────────────────────────────────────────────────────────────────────┐
│                    Frontend (Ionic + Angular 20)                     │
│                    `frontend/src/app`                                │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ Pages (dashboard, trip-view, trip-edit, auth pages)          │   │
│  │ `pages/` — Lazy-loaded standalone components                 │   │
│  └───────────────────────┬──────────────────────────────────────┘   │
│                          │                                            │
│  ┌──────────────────────┴──────────────────────────────────────┐   │
│  │ Components (trip-map, stop-list, ai-suggestion-cards, etc)   │   │
│  │ `pages/trips/components/` — Reusable, fully standalone       │   │
│  └──────────────────────────────────────────────────────────────┘   │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │
                    Typed HttpClient (RxJS)
                    Bearer JWT in Authorization header
                                 │
                ┌────────────────┼────────────────┐
                │                │                │
                ▼                ▼                ▼
    ┌─────────────────┐ ┌──────────────────┐ ┌──────────────────┐
    │  HTTP Core      │ │  Auth Interceptor│ │ Session-Expiry   │
    │  (api-error     │ │  (inject JWT)    │ │ Interceptor      │
    │   mapper)       │ │  `core/          │ │ (403 → logout)   │
    │                 │ │   interceptors/` │ │                  │
    │ `core/http/`    │ └──────────────────┘ └──────────────────┘
    └─────────────────┘
                                 │
                ┌────────────────┼────────────────┐
                │                │                │
                ▼                ▼                ▼
      ┌──────────────────┐ ┌───────────────┐ ┌──────────────────┐
      │ Auth Service     │ │ Trip Service  │ │ StopPhoto Service│
      │ (JWT handling,   │ │ (crud, list,  │ │ (upload to       │
      │  localStorage)   │ │  search,      │ │  Cloudinary)     │
      │ `core/services/` │ │  ai-generate) │ │                  │
      │                  │ │               │ │ `core/services/` │
      └──────────────────┘ └───────────────┘ └──────────────────┘
                                 │
                    ╔════════════════════════╗
                    ║  HTTP Backend API      ║
                    ║  `http://localhost:    ║
                    ║   8080/api`            ║
                    ╚════════════════════════╝
                                 │
    ┌────────────────────────────┼────────────────────────────┐
    │                                                           │
    ▼                                                           ▼
┌───────────────────────────────────────────┐   ┌──────────────────────┐
│         Backend (Spring Boot 4.1)          │   │ External Integrations │
│     `backend/src/main/java/.../backend`    │   └──────────────────────┘
│                                             │
│ ┌─────────────────────────────────────────┐│   ┌─ OpenRouteService ─┐
│ │   REST Controllers                       ││   │   (VROOM, routing) │
│ │   `controller/` — HTTP entry points      ││   └────────────────────┘
│ │   • AuthController: /api/auth/**         ││
│ │   • TripController: /api/trips/**        ││   ┌─ Google Gemini ───┐
│ │   • StopController: /api/trips/{id}...   ││   │ (AI itineraries)  │
│ │   • AiController: /api/trips/{id}/ai-**  ││   └────────────────────┘
│ │   • StopPhotoController: photo upload    ││
│ │   • TripExportController: .ics export    ││   ┌─ Cloudinary ──────┐
│ └────────────────┬─────────────────────────┤   │ (photo storage)   │
│                  │                          │   └────────────────────┘
│ ┌────────────────▼─────────────────────────┐│
│ │   Service Layer (Business Logic)         ││
│ │   `service/` — Transactional, stateless  ││
│ │   • TripService: trip CRUD               ││
│ │   • StopService: stop CRUD (nested)      ││
│ │   • AuthService: user registration/login ││
│ │   • RouteOptimizationService: ORS calls  ││
│ │   • AiItineraryService: Gemini calls     ││
│ │   • AiTripGenerationService: full trip AI││
│ │   • TripOwnershipService: authorization  ││
│ │   • PlaceResolutionService: place lookup ││
│ │   • TripCloneService: trip duplication   ││
│ │   • TripLikeService: like management     ││
│ │   • IcsExportService: calendar export    ││
│ └────────────────┬─────────────────────────┤│
│                  │                          │
│ ┌────────────────▼─────────────────────────┐│
│ │   Repository Layer (Data Access)         ││
│ │   `repository/` — Spring Data JPA        ││
│ │   • TripRepository: Trip CRUD + queries  ││
│ │   • StopRepository: nested stop ops      ││
│ │   • UserRepository: user lookups         ││
│ │   • TripLikeRepository: like tracking    ││
│ │   • TripSearchRepository: text search    ││
│ └────────────────┬─────────────────────────┤│
│                  │                          │
│ ┌────────────────▼─────────────────────────┐│
│ │   Domain (Entity Models)                 ││
│ │   `domain/` — JPA entities, enums        ││
│ │   • User, Trip, Stop, Place, StopPhoto  ││
│ │   • TripVisibility, TripStatus, StopType ││
│ │   • StopStatus, TripLike (composite key) ││
│ └─────────────────────────────────────────┘│
└─────────────────────────────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
                    ▼                         ▼
            ┌─────────────────┐       ┌─────────────────┐
            │  PostgreSQL 16  │       │  JWT Bearer     │
            │  (Flyway DDL)   │       │  Stateless auth │
            └─────────────────┘       └─────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File(s) |
|-----------|----------------|---------|
| **Controllers** | HTTP request routing, validation, response wrapping | `controller/*.java` |
| **Services** | Business logic, ownership checks, external API coordination | `service/*.java` |
| **Repositories** | Database queries, entity persistence (Spring Data JPA) | `repository/*.java` |
| **Domain** | Entity models, enums, relationships | `domain/*.java`, `domain/enums/*.java` |
| **DTOs** | Wire format for requests/responses, field validation | `dto/*.java` |
| **Mappers** | Entity ↔ DTO conversions | `mapper/*.java` |
| **Security** | JWT validation, user principal, auth handlers | `security/*.java` |
| **Clients** | External API integration (ORS, Gemini, Cloudinary) | `client/{service}/*.java` |
| **AI** | Prompt templates, response parsers, structured output | `ai/*.java` |
| **Exception** | Custom exceptions, global error handler, ApiError shape | `exception/*.java` |
| **Config** | Spring beans, properties, datasource, scheduling | `config/*.java` |
| **Ratelimit** | Request rate limiting (per-user, per-IP) | `ratelimit/*.java` |
| **Schedule** | Async tasks (e.g., itinerary dayNumber/plannedTime assignment) | `schedule/*.java` |

## Pattern Overview

**Overall:** Layered + service-oriented (deliberate choice over feature-slicing for Spring Boot conventions)

**Key Characteristics:**
- **Stateless authentication:** JWT bearer tokens only (no cookies, no sessions)
- **Service-scoped authorization:** Ownership checks in services, not Spring Security annotations
- **External client isolation:** Each integration (ORS, Gemini, Cloudinary) has its own client + config module
- **Transaction boundaries:** Short transactions at persistence layer; no holding connections across external calls
- **Single responsibility:** Controllers route, services orchestrate, repositories query, domain represents

## Layers

**Presentation (Frontend):**
- **Purpose:** User interface, client-side state, HTTP calls
- **Location:** `frontend/src/app`
- **Contains:** Pages, components, services, models, interceptors, guards
- **Depends on:** Backend API (HTTP), browser APIs (localStorage, Mapbox)
- **Used by:** End-user browser

**HTTP Entry Point (Backend):**
- **Purpose:** Route requests, extract/validate input, invoke services
- **Location:** `backend/src/main/java/.../backend/controller/`
- **Contains:** `@RestController` classes with `@GetMapping`, `@PostMapping`, etc.
- **Depends on:** Services, mappers, exception handlers
- **Used by:** Frontend HTTP client

**Business Logic (Backend Services):**
- **Purpose:** Implement trip CRUD, route optimization, AI generation, authorization, ownership
- **Location:** `backend/src/main/java/.../backend/service/`
- **Contains:** `@Service` classes with `@Transactional` methods
- **Depends on:** Repositories, domain entities, external clients, mappers
- **Used by:** Controllers, other services (e.g., RouteOptimizationService calls OrsClient)

**Data Access (Backend Repository):**
- **Purpose:** Query and persist domain entities
- **Location:** `backend/src/main/java/.../backend/repository/`
- **Contains:** Spring Data `JpaRepository` interfaces + custom query methods
- **Depends on:** Domain entities, database (PostgreSQL via Flyway)
- **Used by:** Services

**Domain (Backend Entities):**
- **Purpose:** Represent core business objects (Trip, Stop, User, Place, etc.)
- **Location:** `backend/src/main/java/.../backend/domain/`
- **Contains:** JPA `@Entity` classes with relationships, enums for status/visibility
- **Depends on:** Hibernate, JPA annotations
- **Used by:** Repositories, services

**Wire Format (DTOs):**
- **Purpose:** Decouple HTTP contracts from domain entities
- **Location:** `backend/src/main/java/.../backend/dto/`
- **Contains:** Request/response records or POJOs with `@Valid` annotations
- **Depends on:** Jakarta validation, JSON serialization (Jackson)
- **Used by:** Controllers, mappers

**External Integration (Backend Clients):**
- **Purpose:** Isolate third-party API calls and error handling
- **Location:** `backend/src/main/java/.../backend/client/{service}/`
- **Subdirectories:** `ors/`, `gemini/`, `cloudinary/`
- **Contains:** HTTP client, request/response models, properties, exceptions
- **Depends on:** `RestTemplate` or `WebClient`, external API SDKs
- **Used by:** Services (e.g., RouteOptimizationService uses OrsClient)

**Security & Auth:**
- **Purpose:** JWT validation, user authentication, authorization filters
- **Location:** `backend/src/main/java/.../backend/security/`
- **Contains:** `JwtAuthFilter`, `JwtService`, `UserPrincipal`, auth handlers
- **Depends on:** Spring Security, JWT library (jjwt)
- **Used by:** Security filter chain, controllers

## Data Flow

### Primary Request Path: Create Trip

1. **Frontend:** `DashboardPage` → user clicks "Create Trip" → navigates to `TripEditPage`
   - File: `frontend/src/app/pages/trips/trip-edit/trip-edit.page.ts`

2. **Frontend:** User fills form, submits `CreateTripRequest` (title, stops, visibility)
   - File: `frontend/src/app/core/services/trip.service.ts:createTrip()`
   - Calls: `POST /api/trips` with JSON body

3. **Backend Interceptor:** Auth interceptor injects JWT in `Authorization: Bearer {token}` header
   - File: `frontend/src/app/core/interceptors/auth.interceptor.ts`

4. **Backend Security:** `JwtAuthFilter` validates token, extracts userId, sets `UserPrincipal` on `SecurityContext`
   - File: `backend/src/main/java/.../backend/security/JwtAuthFilter.java`

5. **Backend Controller:** `TripController.createTrip()` receives `@Valid CreateTripRequest`, extracts `@AuthenticationPrincipal UserPrincipal principal`
   - File: `backend/src/main/java/.../backend/controller/TripController.java:63–66`
   - Calls: `tripService.createTrip(principal.userId(), request)`

6. **Backend Service:** `TripService.createTrip()` opens transaction
   - File: `backend/src/main/java/.../backend/service/TripService.java:70–83`
   - Loads user (lazy reference): `userRepository.getReferenceById(ownerId)`
   - Maps request → Trip entity: `tripMapper.toEntity(request, owner)`
   - **Place resolution:** Delegates to `StopService.buildStops()`, which calls `PlaceResolutionService.resolvePlace()` for each stop
     - File: `backend/src/main/java/.../backend/service/StopService.java:54–64`
     - Looks up or creates `Place` entities (geocoding cache)
   - Assigns stops to trip: `trip.getStops().addAll(stopService.buildStops(...))`
   - Persists: `tripRepository.save(trip)`
   - Transaction commits; connection released

7. **Backend Mapper:** `TripMapper.toResponse()` converts Trip entity → `TripResponse` DTO
   - File: `backend/src/main/java/.../backend/mapper/TripMapper.java`
   - Includes: trip metadata, stops (via `StopMapper`), but NOT photos (separate endpoint)

8. **Backend Controller:** Returns `ResponseEntity.status(CREATED).body(tripResponse)` — HTTP 201
   - File: `backend/src/main/java/.../backend/controller/TripController.java:66`

9. **Frontend Service:** Receives `TripResponse`, error handlers invoke `mapApiError()` on failure
   - File: `frontend/src/app/core/services/trip.service.ts:42–46`
   - Pipes `catchError()` through `mapApiError()` mapper
   - File: `frontend/src/app/core/http/api-error.mapper.ts`

10. **Frontend Page:** Subscribes to observable, updates component state, navigates to `TripViewPage` on success
    - File: `frontend/src/app/pages/trips/trip-view/trip-view.page.ts`

### Route Optimization Flow

1. **Frontend:** User clicks "Optimize" on `TripViewPage` → calls `tripService.optimizeTrip(tripId)`
   - File: `frontend/src/app/pages/trips/trip-view/trip-view.page.ts`
   - Calls: `POST /api/trips/{id}/optimize` (body: `{}`)

2. **Backend Controller:** `TripController.optimizeTrip()` rate-limits, then calls service
   - File: `backend/src/main/java/.../backend/controller/TripController.java:104–108`

3. **Backend Service:** `RouteOptimizationService.optimize()` (deliberately NOT `@Transactional`)
   - File: `backend/src/main/java/.../backend/service/RouteOptimizationService.java:80+`
   - **Phase 1:** Load trip (separate transaction in `TripOwnershipService`)
     - `tripOwnershipService.loadOwnedTrip(tripId, userId)` → commits → connection closed
   - **Phase 2:** Call ORS VROOM (connection NOT held)
     - `orsClient.optimize(stops)` → sends HTTP POST to OpenRouteService
     - Parses response, reorders stops by optimal visiting order
   - **Phase 3:** Call ORS Directions (connection NOT held)
     - `orsClient.getDirections(orderedStops)` → gets route geometry + leg durations
   - **Phase 4:** Schedule stops (calculate dayNumber/plannedTime from durations)
     - `itineraryScheduler.assignSchedule(trip, durations)` → sets `stop.dayNumber`, `stop.plannedTime`
   - **Phase 5:** Persist (separate transaction in `TripRepository`)
     - `tripRepository.save(trip)` → persists reordered stops + route geometry

4. **Backend Mapper:** `TripMapper.toResponse()` includes stops in new order + route geometry
   - File: `backend/src/main/java/.../backend/controller/TripController.java:107`

5. **Frontend Page:** Receives optimized trip, re-renders `TripMapComponent` with new route
   - File: `frontend/src/app/pages/trips/components/trip-map/trip-map.component.ts`

### AI Itinerary Generation Flow

1. **Frontend:** User clicks "AI Suggestions" on `TripViewPage` → opens modal with `AiPreferencesFormComponent`
   - File: `frontend/src/app/pages/trips/components/ai-preferences-form/ai-preferences-form.component.ts`

2. **Frontend:** User provides preferences (interests, budget, pace) → calls `tripService.suggestItinerary(tripId, preferences)`
   - File: `frontend/src/app/pages/trips/trip-view/trip-view.page.ts`
   - Calls: `POST /api/trips/{id}/ai-suggest` with `ItineraryPreferencesRequest`

3. **Backend Controller:** `AiController.suggestItinerary()` extracts authentication + preferences
   - File: `backend/src/main/java/.../backend/controller/AiController.java`

4. **Backend Service:** `AiItineraryService.suggestItinerary()` (deliberately NOT `@Transactional`)
   - File: `backend/src/main/java/.../backend/service/AiItineraryService.java`
   - **Phase 1:** Load trip (separate transaction in `TripOwnershipService`)
   - **Phase 2:** Build prompt from trip stops + preferences
     - `ItineraryPromptTemplate.render(promptInput)` → renders Gemini prompt
   - **Phase 3:** Call Gemini (connection NOT held)
     - `geminiClient.generateContent(prompt)` → sends HTTP request to Google Gemini
   - **Phase 4:** Parse response
     - `GeminiResponseParser.parse(responseText)` → extracts stop suggestions (JSON or structured format)
   - Returns: `SuggestedItinerary` (list of suggested stops, not persisted)

5. **Backend Controller:** Returns `SuggestedItineraryResponse` (HTTP 200)
   - Suggestions displayed in modal; user can accept individual suggestions

6. **Frontend Page:** If user accepts, calls `tripService.addStop(tripId, suggestion)` for each suggestion
   - File: `frontend/src/app/pages/trips/components/ai-suggestion-cards/ai-suggestion-cards.component.ts`
   - Calls: `POST /api/trips/{id}/stops` (create nested stop)

7. **Backend:** `StopService.addStop()` adds each suggestion to the trip
   - File: `backend/src/main/java/.../backend/service/StopService.java:54–64`

### Authentication Flow

1. **Frontend:** User enters credentials on `LoginPage` or `SignupPage`
   - File: `frontend/src/app/pages/auth/login/login.page.ts`

2. **Frontend:** Calls `authService.login()` or `authService.register()`
   - File: `frontend/src/app/core/services/auth.service.ts`
   - `POST /api/auth/login` or `POST /api/auth/register`

3. **Backend Controller:** `AuthController.login()` or `AuthController.register()`
   - File: `backend/src/main/java/.../backend/controller/AuthController.java`
   - Rate-limits (per IP): `rateLimiterService.checkLimit()`

4. **Backend Service:** `AuthService.login()` or `AuthService.register()`
   - File: `backend/src/main/java/.../backend/service/AuthService.java`
   - Validates credentials or checks for duplicate email
   - Issues JWT: `jwtService.generateToken(userId, email)` — valid 24h (default)

5. **Backend Response:** Returns `AuthResponse { token, userId, username }` — HTTP 200 (or 201 for register)

6. **Frontend:** `authService.handleAuthSuccess()` stores JWT + user in localStorage
   - File: `frontend/src/app/core/services/auth.service.ts:46–50`
   - Sets `isAuthenticated` signal to `true`

7. **Every Subsequent Request:** Auth interceptor injects `Authorization: Bearer {token}`
   - File: `frontend/src/app/core/interceptors/auth.interceptor.ts`

8. **Backend JWT Filter:** `JwtAuthFilter` validates token per-request
   - File: `backend/src/main/java/.../backend/security/JwtAuthFilter.java`
   - If valid: sets `UserPrincipal` on `SecurityContext`, request proceeds
   - If invalid/expired: does NOT throw; request proceeds without principal
   - Controllers that need auth use `@AuthenticationPrincipal UserPrincipal principal` — this is null if no valid token

9. **Session Expiry Interceptor:** If backend returns 403 Forbidden (or 401 from expired JWT)
   - File: `frontend/src/app/core/interceptors/session-expiry.interceptor.ts`
   - Calls `authService.logout()` → clears localStorage, navigates to `/login`

### Error Handling

1. **Validation Errors (400):**
   - **Backend:** `@Valid` annotation on `@RequestBody` → `MethodArgumentNotValidException`
   - **Handler:** `GlobalExceptionHandler.handleValidation()` → `ApiError` with `fieldErrors` array
   - File: `backend/src/main/java/.../backend/exception/GlobalExceptionHandler.java:45–52`
   - **Frontend:** `mapApiError()` extracts `fieldErrors` → displays per-field messages

2. **Not Found (404):**
   - **Backend:** Service throws `ResourceNotFoundException`
   - **Handler:** `GlobalExceptionHandler.handleNotFound()` → `ApiError { status: 404, message: "..." }`
   - **Frontend:** `mapApiError()` shows default "Trip not found" or custom message

3. **Forbidden (403):**
   - **Backend:** Service throws `ForbiddenException` for authorization violations (owner-only checks)
   - **Handler:** `GlobalExceptionHandler.handleForbidden()` → HTTP 403
   - **Frontend:** `mapApiError()` shows default "You do not have permission"
   - **Also:** Session expiry interceptor treats 403 as session expired, logs out

4. **External API Failures (502):**
   - **ORS (Route Optimization):**
     - File: `backend/src/main/java/.../backend/exception/GlobalExceptionHandler.java:73–77`
     - `OrsClient` throws `OrsClientException` on HTTP failure or timeout
     - Handler returns HTTP 502 with message "Route service is temporarily unavailable"
   - **Gemini (AI):**
     - File: `backend/src/main/java/.../backend/exception/GlobalExceptionHandler.java:79–96`
     - `GeminiClient` throws `GeminiClientException` or `GeminiParsingException`
     - Handler returns HTTP 502

5. **Rate Limiting (429):**
   - **Backend:** `RateLimiterService.checkLimit()` throws `RateLimitExceededException`
   - **Handler:** `GlobalExceptionHandler.handleRateLimit()` → HTTP 429
   - **Frontend:** `mapApiError()` shows "Route optimization is rate-limited, try again shortly"

**Rate Limit Scopes:**
- `register:{ip}` — per IP address (e.g., `192.168.1.1`)
- `login:{ip}` — per IP address
- `optimize:{userId}` — per authenticated user

**Canonical ApiError Shape (all error responses):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/trips",
  "timestamp": "2026-08-14T12:00:00Z",
  "fieldErrors": [
    { "field": "title", "message": "must not be blank" }
  ]
}
```

## Key Abstractions

**Trip:**
- Represents a multi-stop itinerary; owned by a user; has visibility (PRIVATE/PUBLIC)
- Persists: title, description, tags, start date, status (DRAFT/IN_PROGRESS/COMPLETED)
- Denormalized: `likeCount` (updated atomically via ORM queries, not Java)
- Contains: ordered list of `Stop` entities
- File: `backend/src/main/java/.../backend/domain/Trip.java`

**Stop:**
- Represents a single destination within a trip; linked to a `Place`
- Persists: stop order (stopOrder), status (PLANNED/VISITED/SKIPPED), notes
- Assigned by optimization: `dayNumber`, `plannedTime` (e.g., "2026-08-15", "09:00:00")
- Contains: `StopPhoto` collection (photos uploaded by user)
- File: `backend/src/main/java/.../backend/domain/Stop.java`

**Place:**
- Represents a unique geographic location (POI or address); shared across trips
- Lookup: `PlaceResolutionService.resolvePlace()` — queries existing Place by name/location, or creates new
- Persists: name, latitude, longitude, address, externalPlaceId (Mapbox, Google, etc.)
- File: `backend/src/main/java/.../backend/domain/Place.java`

**User:**
- Represents an authenticated user account
- Persists: email (unique), username, password (bcrypt), JWT expiry timestamp
- File: `backend/src/main/java/.../backend/domain/User.java`

**UserPrincipal:**
- Spring Security principal (not persisted); holds userId + email from JWT
- Used in controllers: `@AuthenticationPrincipal UserPrincipal principal` → `principal.userId()`
- File: `backend/src/main/java/.../backend/security/UserPrincipal.java`

**TripLike:**
- Composite-key entity: (tripId, userId) — represents a user's like on a trip
- Persisted: denormalized `Trip.likeCount` updated atomically when like row inserted/deleted
- File: `backend/src/main/java/.../backend/domain/TripLike.java`

**StopPhoto:**
- Represents a user-uploaded photo attached to a stop
- Hosted by Cloudinary; backend stores URL + signature
- File: `backend/src/main/java/.../backend/domain/StopPhoto.java`

## Entry Points

**Backend REST Endpoints:**

| Endpoint | Method | Controller | Purpose |
|----------|--------|-----------|---------|
| `/api/auth/login` | POST | `AuthController` | User login (returns JWT) |
| `/api/auth/register` | POST | `AuthController` | User registration |
| `/api/trips` | GET | `TripController` | List authenticated user's trips (paginated) |
| `/api/trips` | POST | `TripController` | Create a new trip (with initial stops) |
| `/api/trips/{id}` | GET | `TripController` | Get trip details (owner sees all; non-owners see PUBLIC only) |
| `/api/trips/{id}` | PUT | `TripController` | Full trip update (title, stops, visibility) |
| `/api/trips/{id}` | DELETE | `TripController` | Delete trip (owner only) |
| `/api/trips/{id}/visibility` | PATCH | `TripController` | Toggle PRIVATE ↔ PUBLIC |
| `/api/trips/{id}/optimize` | POST | `TripController` | Optimize stop order via OpenRouteService VROOM |
| `/api/trips/{id}/clone` | POST | `TripController` | Clone a PUBLIC trip (or your own) to a new PRIVATE trip |
| `/api/trips/{id}/like` | POST | `TripController` | Like a trip (idempotent) |
| `/api/trips/{id}/like` | DELETE | `TripController` | Unlike a trip (idempotent) |
| `/api/trips/{id}/stops` | GET | `StopController` | List stops in a trip |
| `/api/trips/{id}/stops` | POST | `StopController` | Add a stop to a trip |
| `/api/trips/{id}/stops/{stopId}` | GET | `StopController` | Get a stop |
| `/api/trips/{id}/stops/{stopId}` | PUT | `StopController` | Update a stop |
| `/api/trips/{id}/stops/{stopId}` | DELETE | `StopController` | Delete a stop |
| `/api/trips/{id}/ai-generate` | POST | `AiController` | Generate a whole trip from a free-text prompt (Gemini) |
| `/api/trips/{id}/ai-suggest` | POST | `AiController` | Get AI suggestions for an existing trip |
| `/api/trips/{id}/stops/{stopId}/photos` | POST | `StopPhotoController` | Upload a photo to Cloudinary for a stop |
| `/api/trips/{id}/calendar.ics` | GET | `TripExportController` | Export trip as iCalendar format |
| `/api/discovery/trips` | GET | `DiscoveryController` | List PUBLIC trips (browsable, paginated, searchable) |

**Frontend Entry Points:**

| Route | Component | Purpose |
|-------|-----------|---------|
| `/` | Redirects to `/login` | Root route |
| `/login` | `LoginPage` | User login form |
| `/signup` | `SignupPage` | User registration form |
| `/starting-up` | `StartingUpPage` | Splash/loading screen (future feature) |
| `/dashboard` | `DashboardPage` | User's trip list, create/clone/delete trips, AI trip generation |
| `/trips/new` | `TripEditPage` | Create a new trip (form with stops) |
| `/trips/:id` | `TripViewPage` | View trip details, map, stops, AI suggestions, like/unlike |
| `/trips/:id/edit` | `TripEditPage` | Edit trip (title, description, visibility, replace all stops) |

**Spring Boot Application Entrypoint:**
- File: `backend/src/main/java/.../backend/BackendApplication.java`
- Standard `@SpringBootApplication`, runs `SpringApplication.run()` on port 8080

**Angular Application Entrypoint:**
- File: `frontend/src/main.ts` — bootstraps `AppComponent`
- File: `frontend/src/app/app.component.ts` — root component with `<ion-app>` + `<ion-router-outlet>`
- File: `frontend/src/app/app.routes.ts` — standalone routing configuration (lazy-loaded components)

## Architectural Constraints

- **Threading:** Single-threaded event loop (both frontend and backend); backend uses Servlet/Tomcat thread pool
- **Global state:** Frontend uses Angular signals (component-level reactive state); backend uses Spring singleton services (stateless)
- **Circular imports:** Avoided; no known circular dependency chains (layered prevents service-to-service cycles)
- **Transactions:** Services explicitly mark boundaries with `@Transactional`; external API calls held outside transaction blocks (SCRUM-210)
- **Database connections:** Held for as short as possible; NOT held across ORS/Gemini HTTP calls (separate transactions per phase)
- **Rate limiting:** In-memory Guava cache; no distributed rate limiting (single-instance deployment assumed)
- **Session state:** None — JWT bearer token only; `SessionCreationPolicy.STATELESS`

## Anti-Patterns

### N+1 Query (Prevented)

**What happens:** A service loads a trip, then iterates stops — each stop access triggers a separate SELECT on Place (due to lazy loading).

**Why it's wrong:** 10 stops = 11 queries (1 trip + 10 places), defeating pagination/performance.

**Do this instead:** Use `@Query` with explicit `LEFT JOIN FETCH` to load the entire object graph in one query.
- File: `backend/src/main/java/.../backend/repository/TripRepository.java:29–35` (`findWithStopsById`)
- Use: `TripRepository.findWithStopsById(tripId)` whenever you need to map Trip → TripResponse

### Holding DB Connection Across External API Calls (Prevented)

**What happens:** `@Transactional` method calls ORS/Gemini HTTP → connection held open for 10–30s → connection pool exhausted.

**Why it's wrong:** Thread pool saturation, downstream services timeout, cascade failure.

**Do this instead:** Split into phases, each its own transaction (or no transaction). Use separate service beans to enforce boundaries.
- File: `backend/src/main/java/.../backend/service/RouteOptimizationService.java:55–62` (deliberately NOT `@Transactional`)
- File: `backend/src/main/java/.../backend/service/AiItineraryService.java:26–30` (deliberately NOT `@Transactional`)

### Leaking Client Exception to HTTP Response (Prevented)

**What happens:** `OrsClient` throws `OrsClientException` → propagates to controller → returns 500 with stack trace.

**Why it's wrong:** Exposes internal details; client doesn't know it's an external API failure.

**Do this instead:** External client exceptions caught by `GlobalExceptionHandler`, mapped to 502 Bad Gateway.
- File: `backend/src/main/java/.../backend/exception/GlobalExceptionHandler.java:73–77` (ORS), `79–83` (Gemini)

### Logging Sensitive Data (Prevented)

**What happens:** Code logs JWT, password, or API key → appears in logs → exposed in monitoring.

**Why it's wrong:** Credential leakage = security breach.

**Do this instead:** Log identifiers, not secrets. Include context (user ID, action), omit values.
- File: `backend/src/main/java/.../backend/security/JwtAuthFilter.java:44, 50` — log userId, not token
- File: `docs/LOGGING_STANDARD.md` — rules enforced by linting/review

### Confirming Existence of Private Resources (Prevented)

**What happens:** Non-owner requests PRIVATE trip → returns 403 Forbidden → attacker knows trip ID exists.

**Why it's wrong:** Leaks information (information disclosure vulnerability).

**Do this instead:** Return 404 Not Found (indistinguishable from "no such trip").
- File: `backend/src/main/java/.../backend/service/TripService.java:100–112` (getTrip method)
- File: `docs/auth.md:SCRUM-71a` — documented rationale

### Modifying a Denormalized Counter in Java (Prevented)

**What happens:** Read `trip.likeCount`, increment, write → concurrent likes race condition.

**Why it's wrong:** Two concurrent likes may result in count = n + 1 instead of n + 2.

**Do this instead:** Use atomic database UPDATE query (SET likeCount = likeCount + 1).
- File: `backend/src/main/java/.../backend/repository/TripRepository.java:74–76` (incrementLikeCount)
- File: `backend/src/main/java/.../backend/domain/Trip.java:66–70` (denormalized comment)

### Paginating with Fetch-Joined Collections (Prevented)

**What happens:** `SELECT t FROM Trip t JOIN FETCH t.stops` + `Pageable` → Hibernate loads ALL stops into memory, paginates there (HHH90003004).

**Why it's wrong:** Pagination defeats itself; fetching 20 trips + 500 stops each = 10,000 rows in memory.

**Do this instead:** Use projection query (return DTO, not entity) so join doesn't bloat result set.
- File: `backend/src/main/java/.../backend/repository/TripRepository.java:43–50` (findSummariesByUserId)

---

*Architecture analysis: 2026-08-14*
