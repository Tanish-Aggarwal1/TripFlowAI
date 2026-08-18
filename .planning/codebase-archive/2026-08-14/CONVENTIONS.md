# Coding Conventions

**Analysis Date:** 2026-08-06

## Naming Patterns

### Files

**Backend (Java):**
- Services: `FeatureService.java` (e.g., `TripService.java`, `AuthService.java`)
- Controllers: `FeatureController.java` (e.g., `TripController.java`)
- DTOs: `ActionFeatureRequest.java`, `FeatureResponse.java` (e.g., `CreateTripRequest.java`, `TripResponse.java`)
- Mappers: `FeatureMapper.java` (e.g., `TripMapper.java`, `StopMapper.java`)
- Repositories: `FeatureRepository.java` (e.g., `TripRepository.java`)
- Domain: `Feature.java` (e.g., `Trip.java`, `User.java`)
- Exceptions: `FeatureException.java` (e.g., `ResourceNotFoundException.java`, `ForbiddenException.java`)
- Tests: `*Test.java` for unit tests, `*IT.java` for integration tests

**Frontend (Angular/TypeScript):**
- Pages: `feature-name.page.ts`, `feature-name.page.html`, `feature-name.page.scss`
- Components: `component-name.component.ts`, `.html`, `.scss`
- Services: `feature.service.ts` (e.g., `trip.service.ts`, `auth.service.ts`)
- Models/Types: `feature.model.ts` (e.g., `trip.model.ts`)
- Tests: `*.spec.ts` co-located with source file

### Functions and Methods

**Backend:**
- camelCase: `createTrip()`, `listTrips()`, `loadOwnedTrip()`, `handleValidation()`
- Prefixes follow intent: `create*`, `get*`, `list*`, `update*`, `delete*`, `handle*`
- Exception handlers: `handle[ExceptionType]()` (e.g., `handleNotFound()`, `handleForbidden()`)

**Frontend:**
- camelCase: `loadTrips()`, `editTrip()`, `openTrip()`, `confirmDelete()`
- Lifecycle/event handlers: `ionViewWillEnter()`, `onSubmit()`, `onAiTripCreated()`
- Private methods: `private` prefix in declaration (e.g., `private handleError()`)

### Variables and Constants

**Backend:**
- Local variables: camelCase (e.g., `tripId`, `requesterId`, `stop`, `page`)
- Constants: UPPER_SNAKE_CASE (rare in this codebase; mostly rely on immutable records and final fields)
- Mock/test variables: lowercase or camelCase (e.g., `user`, `tripRequest`, `mockPage`)

**Frontend:**
- Component properties: camelCase (e.g., `trips`, `loading`, `error`, `aiModalOpen`)
- Signal-based state: lowercase (e.g., `trips = signal<TripSummaryResponse[]>([])`)
- Event handlers: camelCase starting with `on` (e.g., `onAiModalDismissed()`)

### Types and Classes

**Backend:**
- Records (DTOs): PascalCase (e.g., `CreateTripRequest`, `TripResponse`, `ApiError`)
- Entity classes: PascalCase (e.g., `Trip`, `User`, `Stop`)
- Enums: UPPER_SNAKE_CASE values (e.g., `TripVisibility.PUBLIC`, `TripStatus.DRAFT`)
- Interfaces: PascalCase (e.g., `UserDetails` from Spring Security)

**Frontend:**
- Interfaces/Types: PascalCase (e.g., `CreateTripRequest`, `TripResponse`)
- Type literals: UPPER_SNAKE_CASE (e.g., `TripVisibility = 'PUBLIC' | 'PRIVATE'`)
- Component class: PascalCase with `Page` or `Component` suffix (e.g., `DashboardPage`, `StopListComponent`)

## Code Style

### Formatting

**Backend:**
- Indentation: 4 spaces (standard Java)
- Line length: Reasonable, no hard limit observed
- Imports: Ordered alphabetically within groups (java.*, javax.*, org.*, com.*)

**Frontend:**
- Indentation: 2 spaces (standard Angular)
- Line length: Reasonable, around 100-120 characters observed
- Imports: Ordered by type (built-ins, third-party, local)

### Linting

**Backend:**
- No explicit ESLint-like tool configured; Maven and Spring Boot defaults apply
- Code follows Spring Boot conventions and standard Java style

**Frontend:**
- ESLint with `@angular-eslint` recommended preset (`frontend/.eslintrc.json`)
- Key rules enforced:
  - `@angular-eslint/component-class-suffix`: Classes must end with `Page` or `Component`
  - `@angular-eslint/component-selector`: `app-` prefix, kebab-case (e.g., `app-dashboard`)
  - `@angular-eslint/directive-selector`: `app` attribute prefix, camelCase
  - `@angular-eslint/prefer-inject`: Use `inject()` over constructor DI
- Must run `npm run lint` with zero errors before opening a PR

### Decorators and Annotations

**Backend:**
- `@Slf4j` (Lombok) on all services and controllers for logging
- `@Service`, `@Controller`, `@RestController`, `@Component`, `@Repository` for Spring stereotypes
- `@RequiredArgsConstructor` (Lombok) for constructor-based DI
- `@Transactional` for transaction boundaries
- Validation annotations on DTOs: `@NotBlank`, `@NotEmpty`, `@NotNull`, `@Size`, `@Valid`

**Frontend:**
- `@Component` with `standalone: true`, `imports`, `selector`, `templateUrl`, `styleUrls`
- `@Injectable({ providedIn: 'root' })` for singleton services
- No `@NgModule` — all components and services are standalone

## Import Organization

**Backend:**
Order imports in this sequence:
1. Standard Java (`java.*`, `javax.*`)
2. Third-party (`org.*`, `com.fasterxml.*`, etc.)
3. Spring Framework (`org.springframework.*`)
4. Project imports (`com.tripflow.backend.*`)

Example from `TripService.java`:
```java
import java.util.ArrayList;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.service.StopService;
```

**Frontend:**
Order imports in this sequence:
1. Angular core (`@angular/*`)
2. Ionic standalone components
3. RxJS (`rxjs`, `rxjs/operators`)
4. Third-party (`mapbox-gl`, `ionicons`)
5. Project models and services (`../models/*`, `../services/*`)

Example from `trip.service.ts`:
```typescript
import { inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, tap, catchError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { mapApiError } from '../http/api-error.mapper';
import { TripResponse, CreateTripRequest } from '../models/trip.model';
```

## Error Handling

### Backend Pattern

1. **Domain Exceptions**: Services throw custom domain exceptions:
   - `ResourceNotFoundException` for 404 (entity not found)
   - `ForbiddenException` for 403 (ownership/permission denied)
   - `InvalidCredentialsException` for 401 (auth failure)
   - `DuplicateEmailException`, `DuplicateUsernameException` for 409 (conflict)
   - `InsufficientStopsException`, `OrsClientException`, `GeminiClientException` for specific domain errors

2. **Exception Translation**: `GlobalExceptionHandler` in `backend/src/main/java/com/tripflow/backend/exception/GlobalExceptionHandler.java` maps exceptions to HTTP responses:
   - `@ExceptionHandler` methods for each exception type
   - Returns `ApiError` with `status`, `error`, `message`, `path`, `timestamp`, `fieldErrors`
   - Validation errors (`MethodArgumentNotValidException`) extract field-level errors
   - Client exceptions (ORS, Gemini) return 502 with generic message; detail logged server-side

3. **Logging in Exception Handler**:
   - `ERROR` level for unhandled exceptions with full throwable: `log.error("500 ...", ex.getMessage(), ex)`
   - `WARN` level for handled 4xx/auth failures: `log.warn("401 ...", message)`
   - Never echo full error details to client; log at server-side and return generic message

### Frontend Pattern

1. **Error Mapping**: All HTTP errors mapped through `mapApiError()` in `core/http/api-error.mapper.ts`
2. **Service Error Handling**: Services use RxJS `catchError()` to transform HTTP errors:
   ```typescript
   catchError((err: HttpErrorResponse) => this.handleError(err))
   ```
3. **Component Error Display**: Components subscribe with error handler:
   ```typescript
   error: (err) => {
     this.error = err.message;
     this.loading = false;
   }
   ```
4. **Status-Specific Messages**: Override generic backend messages for specific status codes:
   - 403: "You do not have permission to do that."
   - 404: "Trip not found."
   - Default: Use backend message via `fallbackToBackendMessage: true`

## Logging

**Framework:** SLF4J via Lombok `@Slf4j` annotation

**Levels:**
- `ERROR`: Unhandled exceptions (500s), always include throwable as last argument
- `WARN`: Handled exceptional cases (4xx, auth failures, validation errors)
- `INFO`: Business operation audit events (user registered, trip created/updated/deleted)
- `DEBUG`: Diagnostic detail (JWT auth success, ownership checks, cache hits, external API request/response summaries)
- `TRACE`: Not used

**Message Format:**
- Use SLF4J parameterized messages, never string concatenation:
  ```java
  log.info("Trip created id={} ownerId={} stops={}", saved.getId(), ownerId, saved.getStops().size());
  ```
- Key-value style (`key=value`) for greppability
- Include throwable as last argument for stack traces:
  ```java
  log.error("500 Internal Server Error on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
  ```

**What NEVER to log:**
- Passwords, password hashes, raw credential input
- JWT tokens (any part: header, payload, signature)
- `Authorization` header values
- API keys or secrets (ORS, Gemini, Cloudinary, Mapbox)
- Full request/response bodies containing PII
- Session identifiers or cookies

Log an identifier instead: `userId`, `tripId`, `email` (not password)

**Verification:**
- `grep -R "System\.out" backend/src` should return zero results
- `grep -R "printStackTrace" backend/src` should return zero results

## Comments

### When to Comment

**Explain the "why", not the "what":**
- SCRUM ticket references for non-obvious decisions: `// SCRUM-244a: optional trip-level date anchor`
- Architecture rationale: `// Deliberately not TripOwnershipService.loadOwnedTrip (SCRUM-222/AUDIT-13, reviewed and left as-is):`
- Workarounds and trade-offs: `// Jackson 3 not provided by Boot 4.1 web starters — REF-08 risk item`
- Intentional omissions: `// id, routeGeometry intentionally NOT set from request — server-owned`

### JSDoc / TSDoc

**Backend:**
- Class-level JavaDoc for complex services: `/** Trip CRUD only (SCRUM-215) — stop CRUD lives in ... */`
- Method-level JavaDoc rarely used; logic is self-documenting

**Frontend:**
- Component-level JSDoc comments rare; class names and template are self-documenting
- Comments inline for modal dismissal timing logic and async patterns

### Comment Format

- Single-line: `// Comment here`
- Multi-line: `/** Javadoc line 1. Line 2. */` for classes; `// Comment line 1 \n // Comment line 2` for logic
- SCRUM reference at start: `// SCRUM-XXX: explanation`

## Function Design

### Size

- Services: 5–30 lines typical; large ones broken into private helpers
- Controllers: 5–15 lines per endpoint (request binding + service call + response)
- Example: `TripService.createTrip()` is 8 lines; `TripService.getTrip()` is 10 lines

### Parameters

**Backend:**
- Avoid boolean parameters; use enum or separate methods if needed
- Pass DTOs/entities rather than primitives for complex data
- Use `@PathVariable`, `@RequestBody`, `@RequestParam` annotations in controllers

**Frontend:**
- Use typed parameters (no `any`)
- Avoid long parameter lists; prefer objects for related data
- Services accept request objects: `createTrip(request: CreateTripRequest): Observable<TripResponse>`

### Return Values

**Backend:**
- Services return domain objects or DTOs (never raw entities to client)
- Controllers return `ResponseEntity<T>` with explicit `HttpStatus`
- Methods throw domain exceptions; never return error indicators

**Frontend:**
- Services return `Observable<T>` for all async operations
- Components store local state in `signal<T>()` or plain properties
- Private methods return specific types; no `any`

## Module Design

### Exports

**Backend:**
- Services export through dependency injection (Spring `@Service`)
- Repositories use Spring Data `@Repository`
- No barrel exports; each class lives in its own file

**Frontend:**
- Services: `@Injectable({ providedIn: 'root' })` for singletons
- Models: Export types and interfaces from `*.model.ts` files
- No barrel `index.ts` files; import directly from source files

### Component Structure

**Frontend:**
- Each component/page in its own directory with `.ts`, `.html`, `.scss`, `.spec.ts`
- Shared components in `frontend/src/app/shared/` (if applicable)
- Features organized by domain: `frontend/src/app/pages/trips/`, `pages/auth/`

---

*Convention analysis: 2026-08-06*
