# AJF Module A — Intelligent Trip Engine

**Team:** Route & Reason
**Owners:** Tanish (backend), Neel (frontend)

## What It Does

Takes a user's list of trip stops and turns them into an optimized, AI-assisted itinerary — reordering stops for the shortest route via VROOM, generating AI suggestions via Google Gemini, and rendering the result on an interactive Mapbox map.

## Sprint 1 — Auth Foundation
- Implemented Spring Security + JWT authentication (stateless, BCrypt hashing, custom filter chain).
- Built signup/login UI consuming the auth API.

## Sprint 3 — Security Hardening + Refactor
- Custom `AuthenticationEntryPoint` + `AccessDeniedHandler` (SCRUM-100/REF-11) — replaces Spring Security's default HTML error pages with canonical JSON, demonstrating the strategy-pattern extension points Spring Security exposes for exactly this purpose.
- Typed `UserPrincipal implements UserDetails` (SCRUM-102/REF-13) — replaces a raw `Long` principal + manual string parsing with a proper `UserDetails` implementation resolved via `@AuthenticationPrincipal`, demonstrating idiomatic Spring Security identity handling across the full filter → controller pipeline.
- Typed `JwtProperties` via `@ConfigurationProperties` with `@Validated` (SCRUM-101/REF-12) — fail-fast ≥32-byte secret check at startup, Duration-typed expiry.
- Backend package restructure (SCRUM-197) — layered architecture with dedicated `security/`, `client/`, `ai/` packages.

## Sprint 3 — Route Optimization (VROOM)
- **SCRUM-58:** VROOM multi-stop optimization service — backend integrates with OpenRouteService's VROOM endpoint to reorder stops by shortest travel time. Demonstrates the `client/{ors}/` external-integration pattern: separate wire-format DTOs, `@ConfigurationProperties`, per-client timeouts, translated exceptions (`OrsClientException` → 502).
- **SCRUM-142:** Route optimization integration test (`RouteOptimizationControllerIT`) — uses a nested `@TestConfiguration` with `MockRestServiceServer` to intercept ORS HTTP calls without real network activity. Tests happy path, 502 propagation, 403 non-owner guard, and 422 single-stop validation.

## Sprint 3 — Mapbox Map + Route Rendering
- **SCRUM-59:** Mapbox GL JS map component — numbered stop markers, decoded polyline route rendering, auto-fit bounds, marker popups with stop name/order, and an "Optimize now" fallback banner when `routeGeometry` is null.
- New `trip-view` page and navigation wiring — dashboard row tap navigates to map view, edit page gains a map icon.
- Backend additive change: exposed `routeGeometry` field through `TripResponse` and `TripMapper`.

## Sprint 4 — Gemini AI Integration
- **SCRUM-64/SCRUM-146:** Gemini API client and itinerary generation — full `client/gemini/` package mirroring the `client/ors/` pattern (`GeminiProperties` with API-key masking in `toString()`, `GeminiClientConfig`, `GeminiClient`, wire-format DTOs with `@JsonIgnoreProperties(ignoreUnknown = true)` on all response records).
- `AiItineraryService` — ownership check, prompt template with `{{placeholder}}` substitution, structured response parsing via `GeminiResponseParser` using a locally-configured strict `ObjectMapper`.
- `AiController` with `POST /api/trips/{id}/ai-suggest` — accepts user preferences (interests, budget, pace), returns suggested stops with coordinates and reasoning.
- Two new exceptions (`GeminiClientException`, `GeminiParsingException`) mapped to 502 in `GlobalExceptionHandler`.
- `SuggestedItinerary` schema record with `@JsonIgnoreProperties(ignoreUnknown = false)` — intentionally strict so unexpected Gemini response fields fail loudly rather than being silently dropped.

## Presentation Notes
- **Demo flow:** Create a trip → add stops → hit "Optimize" → watch stops reorder and route redraw on the map → open AI preferences form → generate Gemini suggestions → accept a suggestion as a new stop.
- **Architecture talking points:** The `client/{service}/` pattern (wire DTOs separate from domain DTOs, per-client timeouts, translated exceptions), the strict-vs-lenient ObjectMapper split between Gemini and app-wide JSON, `@ConfigurationProperties` records with validated secrets.
- **Advanced Java concepts demonstrated:** Spring Security extension points (strategy pattern for error handling), `@ConfigurationProperties` with `@Validated` records, `RestClient` with `MockRestServiceServer` for testable external integrations, `@EntityGraph` for N+1 prevention, `OncePerRequestFilter` for JWT processing.