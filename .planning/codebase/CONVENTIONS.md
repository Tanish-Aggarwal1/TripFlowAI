# Coding Conventions

**Analysis Date:** 2026-08-14

## Naming Patterns

**Frontend (TypeScript):**
- Files: `feature-name.component.ts`, `feature.service.ts`, `feature.model.ts` (kebab-case)
- Functions & variables: camelCase (`addInterest()`, `submitting`, `interestInput`)
- Classes & interfaces: PascalCase (`AuthService`, `TripResponse`, `CreateTripRequest`)
- Component selectors: kebab-case with `app` prefix (`app-ai-preferences-form`, enforced by ESLint `@angular-eslint/component-selector`)
- Directive selectors: camelCase with `app` prefix (`appAutoFocus`, enforced by ESLint `@angular-eslint/directive-selector`)
- Component class suffix: required to be either `Page` or `Component` (enforced by ESLint `@angular-eslint/component-class-suffix`)
- Component template files: match component name with `.html` extension
- Component style files: match component name with `.scss` extension

**Backend (Java):**
- Classes: PascalCase (`AuthService`, `AuthController`, `User`, `BaseEntity`)
- Methods & variables: camelCase (`handleAuthSuccess()`, `emailUniqueConstraint`, `passwordEncoder`)
- Constants: UPPER_SNAKE_CASE (`DEFAULT_PROFILE`, `EMAIL_UNIQUE_CONSTRAINT`)
- Packages: lowercase, hierarchical (`com.tripflow.backend.controller`, `com.tripflow.backend.service.ai`)
- Package structure follows layering: `controller/`, `service/`, `repository/`, `domain/`, `dto/`, `mapper/`, `exception/`, `client/`, `security/`, `config/`, `ai/`

## Code Style

**Frontend:**
- Formatter: EditorConfig (`.editorconfig`)
- Indentation: 2 spaces
- Quotes: single quotes in TypeScript (enforced by EditorConfig `quote_type = single`)
- Line endings: LF with final newline
- Trailing whitespace: trimmed (except in markdown)
- Linter: ESLint via `@angular-eslint`
- Angular style: standalone components (`standalone: true`), no NgModules
- Template syntax: new Angular control flow (`@if`, `@for`, `@switch`) instead of `*ngIf`, `*ngFor`, `*ngSwitch`
- Dependency injection: `inject()` function (enforced by `@angular-eslint/prefer-inject`) instead of constructor injection

**Backend:**
- Indentation: 4 spaces (Java standard)
- Code generation: Lombok annotations for boilerplate
  - `@Slf4j` for logging (auto-generates `log` field)
  - `@RequiredArgsConstructor` for constructor injection
  - `@Getter`, `@Setter` for accessors
  - `@Data` used sparingly (favors explicit `@Getter`/`@Setter`)
- No automatic formatting tool configured (team follows Spring Boot conventions)

## Import Organization

**Frontend:**
Order imports by:
1. Angular core (`@angular/core`, `@angular/common`)
2. RxJS (`rxjs`, `rxjs/operators`)
3. Third-party libraries (`@ionic/angular`, `ionicons`)
4. Local services/models (relative imports from `../`)

Example from `auth.service.ts`:
```typescript
import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest } from '../models/auth.model';
import { environment } from '../../../environments/environment';
import { mapApiError } from '../http/api-error.mapper';
```

**Backend:**
Standard Java import order:
1. Java standard library (`java.*`, `jakarta.*`)
2. Third-party libraries (Spring, Lombok, etc.)
3. Internal packages (`com.tripflow.backend.*`)

Wildcard imports avoided; all imports explicit.

## Error Handling

**Frontend:**
- HTTP errors routed through `mapApiError()` function in `api-error.mapper.ts`
- Error messages mapped by status code: 401 → fixed "Invalid credentials", 409 → custom or fallback message, 400 → "Please fix the errors below" if validation errors present
- Network errors (status 0): custom message "Network error. Please check your connection and try again."
- Errors thrown as `throwError(() => error)` from Observable `catchError` handlers
- Error messages sanitized to never leak backend implementation details to the UI

Example from `auth.service.ts`:
```typescript
catchError((err: HttpErrorResponse) => {
  const error = mapApiError(err, {
    messagesByStatus: {
      401: 'Invalid credentials.',
      409: (body) => body?.message ?? 'Email already registered.',
    },
  });
  return throwError(() => error);
})
```

**Backend:**
- Custom exception hierarchy under `com.tripflow.backend.exception.*`
  - `ResourceNotFoundException` → 404
  - `ForbiddenException` → 403
  - `InvalidRequestException` → 400
  - `DuplicateEmailException`, `DuplicateUsernameException` → 409
  - `ConflictException` → 409
  - `InvalidCredentialsException` → 401 (message sanitized)
  - `OrsClientException` → 502
  - `GeminiClientException` → 502
  - `OrsRateLimitException` → 429
  - `GeminiParsingException` → 502
  - `InsufficientStopsException` → 422

- All exceptions caught and mapped to `ApiError` response shape in `GlobalExceptionHandler` (`@RestControllerAdvice`)
- `ApiError` shape: `{ status, error, message, path, timestamp (UTC Instant), fieldErrors (array of {field, message} on 400 only) }`
- `GlobalExceptionHandler` is the single point where unhandled exceptions log at ERROR level

Example from `GlobalExceptionHandler.java`:
```java
@ExceptionHandler(InvalidCredentialsException.class)
public ResponseEntity<ApiError> handleBadCredentials(RuntimeException ex, HttpServletRequest req) {
    log.warn("401 Unauthorized on {}: invalid credentials", req.getRequestURI());
    return error(HttpStatus.UNAUTHORIZED, "Invalid email or password", req, null);
}
```

- Race condition handling: pre-checks (e.g., `existsByEmail()`) followed by database uniqueness constraint catch (`DataIntegrityViolationException`)
  - Example in `AuthService.register()`: checks duplicate email/username first, then catches race condition in `save()` if another request wins

## Logging

**Framework:** SLF4J via Lombok `@Slf4j` (auto-generates `log` field)

**Levels:**

| Level | Use for | Examples |
|-------|---------|----------|
| `ERROR` | Unhandled exceptions requiring human investigation | 500 responses, database failures, external API 5xx after retries |
| `WARN` | Handled exceptional cases: client errors (4xx), auth failures, rejected input | Invalid JWT, validation failure, duplicate email, rate limit exceeded |
| `INFO` | State changes worth auditing; one line per business operation | User registered, user logged in, trip created/updated/deleted |
| `DEBUG` | Diagnostic detail for reproducing issues; off in production | JWT authenticated for user, ownership check, external API request summaries |
| `TRACE` | Not used |

**Message Format:**
- Parameterized messages only (no string concatenation): `log.info("User registered id={} username={}", user.getId(), user.getUsername())`
- Key-value style (`key=value`) for greppability
- Always include throwable as last argument for ERROR level: `log.error("500 on {}: {}", req.getRequestURI(), ex.getMessage(), ex)`

**What NEVER to log:**
- Passwords, password hashes, raw credentials
- JWT tokens (header, payload, signature)
- `Authorization` header values
- API keys or secrets (ORS, Gemini, Cloudinary, Mapbox)
- Full request/response bodies containing PII
- Session identifiers or cookies

Log identifiers instead: `log.debug("User authenticated userId={}", userId)` not the JWT.

**No System.out or printStackTrace()** — all logging via SLF4J.

## Comments

**JavaDoc/TSDoc:**
- Used for complex logic, non-obvious implementations, and public API contracts
- Example from `BaseEntity.java`: detailed identity contract comment explaining equals/hashCode behavior
- Block comments (`/** ... */`) for multi-line documentation; inline comments (`//`) for tactical notes

**Frontend (TypeScript):**
- Component selectors documented with purpose: `selector: 'app-ai-preferences-form'`
- Component comments explain SCRUM ticket context and design rationale
- Example: `// SCRUM-67a / SCRUM-155: collects interests/budget/pace...`

**Regression tests:**
Regression tests include detailed comments explaining the bug they prevent
- Example from `ItineraryPromptTemplateTest.java`: "chained String.replace() calls used to re-scan the whole string on each call, so an interest literally containing `{{budget}}` would get overwritten..."

## Function Design

**Size:** No enforced length limit; functions should fit on a screen and express a single responsibility

**Parameters:** 
- Backend: constructor injection via `@RequiredArgsConstructor` (no `new` keywords in methods)
- Frontend: dependency injection via `inject()` in component constructor or method body
- Keep parameter lists short; bundle related parameters into DTOs/interfaces

**Return Values:**
- Backend: explicit return types, wrap in `ResponseEntity<T>` for HTTP endpoints
- Frontend: Observable return types for HTTP calls; use RxJS operators for transformation

**Async/Promises:**
- Frontend: RxJS Observables throughout; Angular's HttpClient returns Observables
- Components subscribe via `.subscribe({ next, error })` pattern
- Use `pipe()` for chaining operators (tap, catchError, etc.)

## Module Design

**Backend - Package by Layer:**
- `controller/`: HTTP endpoints, request routing, validation decoration
- `service/`: business logic, orchestration, external API calls
- `repository/`: database access via Spring Data JPA
- `domain/`: entity classes, value objects, enums
- `dto/`: data transfer objects for requests/responses (separate from domain)
- `mapper/`: convert between entities and DTOs
- `exception/`: custom exception classes
- `client/`: external service clients (OrsClient, GeminiClient, etc.)
  - Wire-format DTOs separate from domain DTOs
  - Per-client `@ConfigurationProperties` records (e.g., `OrsProperties`, `GeminiProperties`)
  - Client exceptions translated to appropriate HTTP status in GlobalExceptionHandler
- `security/`: JWT service, auth filter, principal classes
- `config/`: Spring configuration beans, properties
- `ai/`: AI-specific logic (prompt templates, response parsing)

**Frontend - Feature-Based Organization:**
- `app/core/`: shared services, interceptors, guards, models, HTTP utilities (not feature-specific)
- `app/pages/`: page components and their sub-components
  - Each feature has a `page.ts` and a `components/` subdirectory
  - Example: `app/pages/trips/trips.page.ts`, `app/pages/trips/components/ai-preferences-form/`
- `app/core/services/`: feature-agnostic services (AuthService, TripService, ToastService)
- `app/core/models/`: TypeScript interfaces and types
- `app/core/guards/`: route guards (AuthGuard)
- `app/core/interceptors/`: HTTP interceptors (AuthInterceptor, SessionExpiryInterceptor, BackendAvailabilityInterceptor)
- `app/core/http/`: HTTP utilities (api-error.mapper.ts)

**Routing:**
- Defined in `app.routes.ts` as an array of route objects
- Route guards are functions (not classes)
- Components lazy-loaded via `loadComponent`

**Environment Configuration:**
- `environment.ts` (dev), `environment.prod.ts` (production) — committed with placeholder tokens (e.g., `__MAPBOX_TOKEN__`)
- `environment.local.ts` (gitignored, local dev only) — swapped in by `angular.json` fileReplacements for development builds
- Backend API base URL from `environment.apiBaseUrl`
- Mapbox token in `environment.mapboxToken`
- Production token values injected at CI build time via `sed` substitution

**Exports & Barrel Files:**
- Barrel files (`index.ts`) used sparingly; prefer direct imports for clarity
- No re-exports of framework modules (import `@angular/core` directly, don't re-export)

---

*Convention analysis: 2026-08-14*
