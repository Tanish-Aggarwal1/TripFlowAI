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

## Sprint 5 — AI Preferences UI + Regression Coverage
- **SCRUM-155:** `AiPreferencesFormComponent` — standalone frontend form (interests, budget, pace) that collects the preferences payload sent to `POST /api/trips/{id}/ai-suggest`.
- **SCRUM-156:** `AiSuggestionCardsComponent` — renders Gemini's suggested stops with reasoning, wired into `trip-view` as a modal; `TripService.addStop` added for the nested stop-create endpoint so an accepted suggestion is persisted as a real stop. Completes the demo-flow loop already described below.
- **SCRUM-68:** `AiControllerIT` regression coverage — Gemini timeout, empty-candidates, and prompt-too-large cases; `RouteOptimizationControllerIT` and `TripControllerIT` gained a create-to-optimize single-flow IT and a delete-cascade IT.

## Sprint 6 — AI Trip Generation + Quick Stop Completion
- **SCRUM-256:** `POST /api/trips/ai-generate` — generates a brand-new trip from a free-text prompt in one call. New `AiTripGenerationService` renders a from-scratch prompt template (`generate-trip.txt`, distinct from `itinerary.txt`'s "add more stops" framing), calls Gemini, and persists via the existing `TripService.createTrip` — nothing is saved if Gemini returns zero stops (422 instead). `GeminiResponseParser` generified to parse into any target schema. Dashboard FAB became a speed-dial ("New Trip" / "Create with AI"); its own rate-limit bucket (5/hour) keeps it separate from `ai-suggest`'s.
- **SCRUM-257:** One-tap "Visited" button directly on each stop row in `trip-view` — reuses the existing `PUT /api/trips/{tripId}/stops/{stopId}` endpoint's optional `status` field, no backend change needed. Swaps to a static checkmark-circle badge once VISITED; per-row in-flight guard against double-tap.

## Presentation Notes
- **Demo flow:** From the dashboard, tap "Create with AI" and describe a trip to generate one from scratch — or create a trip manually and add stops → hit "Optimize" → watch stops reorder and route redraw on the map → open AI preferences form → generate Gemini suggestions → accept a suggestion as a new stop → tap the checkmark on a stop to mark it visited as you go.
- **Architecture talking points:** The `client/{service}/` pattern (wire DTOs separate from domain DTOs, per-client timeouts, translated exceptions), the strict-vs-lenient ObjectMapper split between Gemini and app-wide JSON, `@ConfigurationProperties` records with validated secrets.
- **Advanced Java concepts demonstrated:** Spring Security extension points (strategy pattern for error handling), `@ConfigurationProperties` with `@Validated` records, `RestClient` with `MockRestServiceServer` for testable external integrations, `@EntityGraph` for N+1 prevention, `OncePerRequestFilter` for JWT processing.