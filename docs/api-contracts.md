# API Contracts

Living document. Add a new section per epic as endpoints are built. Update if a contract changes — announce the change in team chat when it does.

## Auth (Sprint 1)

### POST /api/auth/register
**Request:**
```json
{
  "username": "string",
  "email": "string",
  "password": "string"
}
```
**Success (201):**
```json
{
  "token": "string",
  "tokenType": "Bearer",
  "userId": 1,
  "username": "string",
  "expiresAt": "ISO-8601 datetime"
}
```
**Errors:**
- 409 — email already registered
- 409 — username already taken
- 400 — validation failure (see standard error shape below)
- 429 — rate limit exceeded (per-IP, `app.ratelimit.register.*` — see Rate Limiting below; `Retry-After` header included)

### POST /api/auth/login
**Request:**
```json
{
  "email": "string",
  "password": "string"
}
```
**Success (200):** same shape as register
**Errors:**
- 401 — invalid credentials
- 429 — rate limit exceeded (per-IP, `app.ratelimit.login.*` — see Rate Limiting below; `Retry-After` header included)

### Refresh cookie (issued by register and login)

Both endpoints additionally set a refresh-token cookie alongside the JSON body:

`Set-Cookie: refresh_token=<opaque>; Path=/api/auth; Max-Age=2592000; Secure; HttpOnly; SameSite=None`

Host-only by design — no `Domain` attribute is ever set, so the cookie is never exposed to other tenants on the shared PaaS suffix. `Secure`/`SameSite` are overridable for local development via `REFRESH_COOKIE_SECURE` / `REFRESH_COOKIE_SAME_SITE`; production must not override them. Lifetime is `app.refresh-token.expiration-days` (default 30), fixed from issuance rather than sliding. The raw value never appears in a response body and is never persisted — only its SHA-256 hex digest reaches the database.

### POST /api/auth/refresh
Redeems the refresh cookie exactly once and returns a fresh access token plus a rotated cookie.

**Request:** no body. Requires the `refresh_token` cookie **and** an `X-Requested-With` header (any value). The header is the CSRF control: a cross-site form or image cannot set it, so the browser is forced into a CORS preflight that only an allow-listed origin passes. `SameSite` cannot carry this protection because the deployed frontend and backend are different subdomains of a shared PaaS suffix.

**Success (200):**
```json
{
  "token": "string",
  "tokenType": "Bearer",
  "expiresAt": "ISO-8601 datetime"
}
```
No `userId`/`username` — the client already holds them from login. The response also carries a new `refresh_token` cookie with the same attributes as above; the presented value is single-use and is rejected on any subsequent presentation.

**Errors:**
- 400 — `X-Requested-With` header missing (checked before any token lookup)
- 401 — cookie absent, unknown, already redeemed, revoked, or expired (one generic message for all cases)
- 429 — rate limit exceeded (per-IP, `app.ratelimit.refresh.*` — see Rate Limiting below; `Retry-After` header included)

**Replaying an already-redeemed cookie revokes every refresh token that user holds**, on every device, and returns 401 — presenting a consumed token means two parties hold the same value, which is treated as theft rather than as a retry. Access tokens already issued are stateless and keep working until they expire, so a mass revoke takes full effect within the 15-minute access-token lifetime, not instantly.

### POST /api/auth/logout
Ends the session whose refresh cookie was presented and clears that cookie.

**Request:** no body. Takes the `refresh_token` cookie and requires the same `X-Requested-With` header as refresh — logout mutates server state on the strength of a cookie, so it needs the same CSRF control.

**Success (204):** empty body. The response always carries a clearing `Set-Cookie` for `refresh_token` with a zero max-age and the same name, path, `Secure`, `SameSite` and `HttpOnly` attributes as the issuing cookie.

Returned unconditionally — a valid, expired, already-revoked, unknown or entirely absent cookie all produce 204, so logout is idempotent and never reveals whether a given cookie was still worth anything. Only the presented token is revoked; the user's other devices stay signed in.

**Errors:**
- 400 — `X-Requested-With` header missing

Login and register response bodies are unchanged by any of the above.


## Auth Header
Protected endpoints require: `Authorization: Bearer <token>`

Missing, malformed, or expired token → `401 Unauthorized` with the standard `ApiError` body (see below). Valid token but not authorized for the resource (e.g. non-owner) → `403 Forbidden`, same body shape. See `docs/auth.md` for the full breakdown of which mechanism handles which case.

---

## Trips & Stops (SCRUM-52)

### GET /api/trips (paginated — REF-21; search/filter — SEARCH-01)
Returns a page of the authenticated user's trips as a card-sized projection — no `stops` array, just `stopCount` — so list reads never need the collection fetch join used by `GET /api/trips/{id}`. This is the canonical paging contract for all future top-level list endpoints (see `GET /api/discovery/trips` and `GET /api/discovery/search` below, which follow it): accept Spring `Pageable` (`?page=&size=&sort=`), return this same paged shape. **Exception:** the two nested-collection reads, `GET /api/trips/{tripId}/stops` and `GET /api/stops/{stopId}/photos`, intentionally return plain unbounded arrays instead — see their own sections for why.

**Query params:**
- `page` (0-indexed, default 0), `size` (default 20), `sort` (default `createdAt,desc`, e.g. `?sort=title,asc`) — `sort` only applies to the plain (unfiltered, no-search) list; see ordering note below.
- `search` (optional) — matches the trip's title, tags, or the place name of any of its stops. Independently optional from every filter below (D-12). A blank/absent `search` returns the full, unfiltered list rather than a 400 (unlike `GET /api/discovery/search`, where `q` is required) — a search matching nothing is a `200` with an empty `content` array and `totalElements: 0`, never a `404`.
- `status` (optional) — one of `DRAFT | PLANNED | ACTIVE | COMPLETED`. A value outside this set is a `400` (see Standard Error Shape below), never a `500`.
- `visibility` (optional) — `PRIVATE | PUBLIC`.
- `startDateFrom` / `startDateTo` (optional, ISO `YYYY-MM-DD`) — filter on the trip's own `startDate` (when the trip happens), **not** `createdAt` (when the row was created).
- `durationDays` (optional, integer) — the trip's day span, computed as `MAX(dayNumber)` across its stops, not a stored column. A trip with zero stops (or whose stops have never been scheduled by `/optimize`) reports duration `0` rather than being excluded outright — consistent with the zero-stops convention below. This is filter-only; it is not returned as a response field.

All supplied filters AND together (e.g. `?search=paris&status=ACTIVE&visibility=PUBLIC&startDateFrom=2026-06-01&durationDays=3` narrows to trips matching every one of them). When `search` or any filter is present, result ordering is fixed at `createdAt DESC, id DESC` (the `id` tiebreaker keeps ordering stable and repeatable when two trips share a creation instant) — the plain, unfiltered path honours `sort` instead.

**Success (200):**
```json
{
  "content": [
    {
      "id": 1,
      "title": "string",
      "visibility": "PRIVATE | PUBLIC",
      "status": "string",
      "createdAt": "ISO-8601 datetime",
      "updatedAt": "ISO-8601 datetime",
      "stopCount": 3,
      "coverPhotoUrl": null,
      "visitedStopCount": 1,
      "completionPercentage": 0.3333333333333333
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```
`coverPhotoUrl` is always `null` — the Cloudinary photo feature (SCRUM-66/152/153) has shipped, but nothing computes a trip's cover photo from its stops' photos yet; `TripRepository.findSummariesByUserId`/`findSummariesByVisibility` both pass a literal `null` for this field. Treat it as reserved for a future feature, not currently wired up. Use `GET /api/trips/{id}` for the full itinerary including `stops`.

`visitedStopCount`/`completionPercentage` (EXPORT-03) are owner-only — this endpoint's response is `TripOwnerSummaryResponse`, a sibling of the leaner `TripSummaryResponse` the public discovery feed uses (see Discovery below), so a stranger's `PUBLIC` trip never exposes another user's completion progress. `completionPercentage` is a `0.0`–`1.0` fraction (`visitedStopCount / stopCount`); only `StopStatus.VISITED` counts toward it, `SKIPPED` stays in the denominator but not the numerator. A zero-stop trip reports `completionPercentage: 0`, never `null`.

### POST /api/trips
**Request:**
```json
{
  "title": "string",
  "description": "string",
  "tags": ["string"],
  "visibility": "PRIVATE | PUBLIC",
  "stops": [
    {
      "name": "string",
      "latitude": 0.0,
      "longitude": 0.0,
      "address": "string",
      "externalPlaceId": "string"
    }
  ],
  "startDate": "2026-08-10"
}
```
`startDate` is optional (SCRUM-244a) — a `LocalDate` (`YYYY-MM-DD`). Not required for stops to get a `dayNumber`/`plannedTime`; those are trip-relative (day 1, day 2, ...) regardless of whether `startDate` is set. Purely informational until a future feature anchors it to real calendar dates.

**Success (201):** single full trip object — **not** the same shape as the `GET /api/trips` list item (`TripSummaryResponse`). This is a full `TripResponse`: `id`, `title`, `description`, `tags`, `visibility`, `status`, `ownerId`, `stops[]`, `createdAt`, `updatedAt`, `routeGeometry`, `startDate`, `likeCount` — see the full example under "Route Optimization" below for the exact shape (same DTO, `routeGeometry` will just be `null` and `likeCount` `0` for a freshly created trip).
**Errors:**
- 400 — validation failure

**Field limits (SCRUM-212 / AUDIT-03)** — enforced by Bean Validation, mirroring the DB column widths and coordinate domain exactly. A violation returns 400 with a `fieldErrors` entry naming the field, never a 500 from a downstream DB constraint:

| Field | Limit |
| --- | --- |
| `title` | max 150 chars |
| `description` | max 5000 chars (SCRUM-417) |
| `tags` | max 20 elements, each max 50 chars |
| `stops` | max 50 elements (`UpdateTripRequest.MAX_STOPS`) — each stop costs a VROOM job on `/optimize` plus a waypoint on the follow-up directions call, both against a 500 req/day ORS quota |
| `stops[].name` | max 200 chars |
| `stops[].address` | max 300 chars |
| `stops[].externalPlaceId` | max 150 chars |
| `stops[].notes` | max 2000 chars (SCRUM-417) |
| `stops[].latitude` | -90.0 to 90.0 |
| `stops[].longitude` | -180.0 to 180.0 |

Same per-field limits apply to `PUT /api/trips/{id}` (via `UpsertStopRequest` — see that endpoint's section for how it differs from `CreateStopRequest`) and the nested stop endpoints (`POST`/`PUT /api/trips/{tripId}/stops[/{stopId}]`, which share `CreateStopRequest`/`UpdateStopRequest`).

**Global request-body cap (SCRUM-417):** independent of the per-field limits above, every request body is capped at `app.request.max-body-size-bytes` (default 5 MiB) by `RequestSizeLimitFilter` — a backstop against a payload sized to exhaust heap/storage before Bean Validation ever runs, since Tomcat's own size properties (`max-http-form-post-size`, `max-swallow-size`) don't cover JSON bodies. A body over the cap is rejected with `413 Payload Too Large`, whether the size is declared upfront via `Content-Length` or only discovered mid-read (e.g. chunked transfer encoding).

### GET /api/trips/{id}
Owner sees any trip; non-owner only sees `PUBLIC` trips.
**Success (200):** single trip object.
**Errors:**
- 404 — trip not found, **or** the trip is `PRIVATE` and requester is not the owner (same 404 either way so existence isn't leaked — no 403 path on this endpoint; this line previously said 403, which did not match `TripService.getTrip`)

### PATCH /api/trips/{id}/visibility (SCRUM-159)
Flips `PRIVATE` &lt;-&gt; `PUBLIC`. Owner-only, no request body — there is no way to set an explicit target value, only toggle.
**Request:** No body.
**Success (200):** updated trip object (full `TripResponse`, same shape as `GET /api/trips/{id}`).
**Errors:**
- 404 — trip not found
- 403 — requester is not the owner

### PUT /api/trips/{id}
Full itinerary update — metadata + stops in one call, merged by stop identity rather than a blind replace (fixed in the SCRUM-274 review; previously any edit — even a title-only change — silently wiped every stop's `status`/`dayNumber`/`plannedTime`/`stopType` and cascade-deleted its photos, since the old behavior deleted and recreated every stop on every call).

**Request:** mostly the same shape as POST, except each entry in `stops[]` is an `UpsertStopRequest` — `CreateStopRequest`'s fields (`name`, `latitude`, `longitude`, `address`, `externalPlaceId`, `notes`) plus a leading, optional `id`:
```json
{
  "title": "string",
  "description": "string",
  "tags": ["string"],
  "visibility": "PRIVATE | PUBLIC",
  "stops": [
    {
      "id": 1,
      "name": "string",
      "latitude": 0.0,
      "longitude": 0.0,
      "address": "string",
      "externalPlaceId": "string",
      "notes": "string"
    }
  ],
  "startDate": "2026-08-10"
}
```
Merge semantics, by each stop's `id`:
- `id` matches a stop already on this trip → updated in place; its server-owned `status`, `dayNumber`, `plannedTime`, `stopType`, and its `stop_photos` rows survive.
- `id` omitted/`null` → inserted as a new stop.
- an existing stop whose `id` is absent from the payload → deleted (its `Place` row survives if referenced elsewhere) — the one intentional deletion path.
- `id` belongs to a **different** trip (or was already claimed earlier in the same payload) → the whole request fails with 404, both trips left untouched; never silently re-parented.

`startDate` absent or `null` means **leave unchanged**, not clear — a JSON body can't distinguish an omitted field from an explicit `null`, so treating `null` as "clear" would silently wipe the date on every update that didn't restate it. There's currently no way to clear an already-set `startDate` through this endpoint.

**Success (200):** updated trip object.
**Errors:**
- 404 — trip not found, **or** a `stops[].id` in the payload doesn't belong to this trip
- 403 — requester is not the owner
- 400 — validation failure (including `stops` exceeding the 50-element cap)

### DELETE /api/trips/{id}
**Success (204):** no body.
**Errors:**
- 404 — trip not found
- 403 — requester is not the owner

### GET /api/trips/{tripId}/stops
Owner-only (no public read on this sub-resource — use GET /api/trips/{id} for public itinerary viewing). Intentionally **not** paginated (unlike `GET /api/trips` — see REF-21 note above) — stops-per-trip is expected to stay small; there is no application-level cap enforced today.
**Success (200):**
```json
[
  {
    "id": 1,
    "name": "string",
    "latitude": 0.0,
    "longitude": 0.0,
    "address": "string",
    "stopOrder": 0,
    "status": "PLANNED | VISITED | SKIPPED",
    "notes": "string",
    "dayNumber": 1,
    "plannedTime": "09:00:00",
    "stopType": "SIGHTSEEING | MEAL | LODGING | OTHER"
  }
]
```
`dayNumber`/`plannedTime` (SCRUM-244a) are `null` until the trip has been (re-)optimized at least once — `POST /api/trips/{id}/optimize` is the only thing that ever sets them, via a heuristic scheduler (see that endpoint's section below). `stopType` defaults to `SIGHTSEEING` and is not yet settable from any request — it's a foundation field for future AI-driven scheduling (meal-stop suggestions).

### POST /api/trips/{tripId}/stops
Appends a stop at the next `stopOrder`.
**Request:**
```json
{
  "name": "string",
  "latitude": 0.0,
  "longitude": 0.0,
  "address": "string",
  "externalPlaceId": "string"
}
```
**Success (201):** single stop object.
**Errors:** 404 (trip not found), 403 (not owner), 400 (validation)

### GET /api/trips/{tripId}/stops/{stopId}
**Success (200):** single stop object.
**Errors:** 404, 403

### PUT /api/trips/{tripId}/stops/{stopId}
**Request:**
```json
{
  "name": "string",
  "latitude": 0.0,
  "longitude": 0.0,
  "address": "string",
  "externalPlaceId": "string",
  "notes": "string",
  "status": "PLANNED | VISITED | SKIPPED"
}
```
`status` is optional — omit to leave unchanged.
**Success (200):** updated stop object.
**Errors:** 404, 403, 400

### DELETE /api/trips/{tripId}/stops/{stopId}
Remaining stops are automatically renumbered (`stopOrder` closes the gap).
**Success (204):** no body.
**Errors:** 404, 403

---


## Route Optimization (SCRUM-58)

### POST /api/trips/{id}/optimize
Reorders the trip's stops for shortest travel time via OpenRouteService VROOM. Requires ≥2 stops with valid coordinates.

**Auth:** Bearer token required. Only the trip owner can optimize.

**Request:** No body — the endpoint reads the trip's existing stops.

**Success (200):** Returns the full `TripResponse` with stops reordered by optimized `stopOrder` and `routeGeometry` populated. This is the canonical shape of a full trip object, returned by every endpoint documented elsewhere in this file as "single trip object" / "full `TripResponse`" (`POST`/`GET`/`PUT /api/trips[/{id}]`, `PATCH .../visibility`, `POST .../clone`, `POST /api/trips/ai-generate`):
```json
{
  "id": 1,
  "title": "string",
  "description": "string",
  "tags": ["string"],
  "visibility": "PRIVATE",
  "status": "DRAFT",
  "ownerId": 1,
  "stops": [
    {
      "id": 1,
      "name": "string",
      "latitude": 43.65,
      "longitude": -79.38,
      "address": "string",
      "stopOrder": 0,
      "status": "PLANNED | VISITED | SKIPPED",
      "notes": "string",
      "dayNumber": 1,
      "plannedTime": "09:00:00",
      "stopType": "SIGHTSEEING"
    }
  ],
  "createdAt": "2026-07-20T15:30:00Z",
  "updatedAt": "2026-07-20T15:31:00Z",
  "routeGeometry": "{\"type\":\"LineString\",\"coordinates\":[[-79.38,43.65],[-79.40,43.66]]}",
  "startDate": "2026-08-10",
  "likeCount": 0,
  "visitedStopCount": 0,
  "completionPercentage": 0.0
}
```
`routeGeometry` is a **JSON-encoded GeoJSON `LineString` geometry** (`{"type": "LineString", "coordinates": [[lng, lat], ...]}`), stored and returned as a JSON string (`TripResponse.routeGeometry` is typed `String` — the client must `JSON.parse()` it, it is not pre-parsed) — **not an encoded polyline**, despite what earlier revisions of this doc said. Source: `RouteOptimizationService` does `objectMapper.writeValueAsString(geometry)` where `geometry` is ORS's own GeoJSON `Feature.geometry` from the directions response.

(`orderIndex` in earlier revisions of this doc was wrong — the field has always been `stopOrder`, see `StopResponse.java`. `likeCount` was added by SCRUM-161, `startDate` by SCRUM-244a, `visitedStopCount`/`completionPercentage` by SCRUM-EXPORT-03 — see the `GET /api/trips` section above for their exact semantics, which apply identically here.)

**Scheduling (SCRUM-244a):** In addition to reordering stops and computing route geometry, this endpoint runs a heuristic day/time scheduler over the optimized stop order — no Gemini involvement, just a greedy walk assigning each stop a `dayNumber` and `plannedTime`, using the per-leg travel durations from the same ORS directions call already made for route geometry. Each stop is assumed to take `app.schedule.default-visit-duration` (default 1h) to visit; cumulative time rolls to the next day once it would exceed the configured day window (`app.schedule.day-start-time`/`app.schedule.day-end-time`, default `09:00`–`21:00`). This is a foundation for future AI-driven scheduling — see `docs/TripFlow_fall_Break_Plan.md` FB-17/FB-18 for what's planned on top of it.

**Rate limit (SCRUM-173):** Capped per authenticated user at `app.ratelimit.optimize.capacity` requests per `app.ratelimit.optimize.window` (default 20/hour) — see the Rate Limiting section below.

**Errors:**
- 403 — authenticated user is not the trip owner
- 404 — trip not found
- 422 — trip has fewer than 2 stops (nothing to optimize)
- 502 — OpenRouteService is unreachable or returned a server error (`OrsClientException`)
- 429 — either OpenRouteService's own rate limit was hit (`OrsRateLimitException`, no `Retry-After` header), or the caller exceeded their own per-user limit on this endpoint (`RateLimitExceededException`, includes a `Retry-After` header — see below)

All errors return the standard `ApiError` body.

---

## Trip Cloning & Likes (SCRUM-161 / SCRUM-162)

### POST /api/trips/{id}/clone
Deep-copies a `PUBLIC` trip (or your own trip, any visibility) into your account as a new `PRIVATE` trip. Stops are cloned in the same order; `Place` rows are shared, not duplicated. Photos and likes are **not** copied — the clone starts with zero of both.

**Auth:** Bearer token required.

**Request:** No body.

**Success (201):** the new trip, full `TripResponse` (same shape as `POST /api/trips`).
**Errors:**
- 404 — trip not found, **or** the trip is `PRIVATE` and belongs to someone else (same 404 either way so existence isn't leaked — no 403 path on this endpoint)

### POST /api/trips/{id}/like
Likes a trip. Idempotent — liking an already-liked trip still returns 200 and does not double-count. Allowed on any `PUBLIC` trip, or your own `PRIVATE` trips.

**Auth:** Bearer token required.

**Request:** No body.

**Success (200):** no body.
**Errors:**
- 404 — trip not found, **or** the trip is `PRIVATE` and belongs to someone else (same 404 either way — no 403 path)

### DELETE /api/trips/{id}/like
Unlikes a trip. Idempotent — unliking a trip you haven't liked still returns 200.

**Auth:** Bearer token required.

**Request:** No body.

**Success (200):** no body.
**Errors:**
- 404 — trip not found, **or** the trip is `PRIVATE` and belongs to someone else

**Note:** a trip's current like count is the `likeCount` field on the full `TripResponse` (see the Route Optimization section above for the full shape) — there is no separate "get like count" endpoint.

---

## Discovery (SCRUM-160 / SCRUM-163)

Public, unauthenticated browsing of `PUBLIC` trips — no `Authorization` header required on either endpoint. Both follow the same paged shape as `GET /api/trips` (see REF-21 note above).

### GET /api/discovery/trips
Paginated feed of all `PUBLIC` trips, newest first by default.

**Auth:** None required.

**Query params:** same as `GET /api/trips` — `page`, `size`, `sort` (default `createdAt,desc`).

**Success (200):** same paged shape as `GET /api/trips` (`content[]` of `TripSummaryResponse` + `page` block).
**Errors:** none beyond standard validation of `page`/`size`/`sort`.

### GET /api/discovery/search
Case-insensitive substring search over `PUBLIC` trip titles and tags. `q` is required and must not be blank.

**Auth:** None required.

**Query params:** `q` (required, non-blank), plus `page`/`size`/`sort` as above.

**Success (200):** same paged shape as `GET /api/trips`.
**Errors:**
- 400 — `q` missing or blank

**Both discovery endpoints deliberately keep the leaner `TripSummaryResponse` shape — no `visitedStopCount`/`completionPercentage` (D-08).** These endpoints are unauthenticated and cross-owner by nature (anyone browsing `PUBLIC` trips), so serving another user's completion progress here would leak it to strangers. `GET /api/trips`'s owner-only `TripOwnerSummaryResponse` is a separate DTO for exactly this reason — do not "fix" this asymmetry by adding the fields here; it is intentional, not an oversight.

---

## AI Itinerary Suggestions (SCRUM-64 / SCRUM-146)

### POST /api/trips/{id}/ai-suggest
Sends user preferences to Google Gemini and returns structured itinerary suggestions. Does not persist anything — the frontend accepts individual stops via the existing `POST /api/trips/{id}/stops` endpoint.

**Auth:** Bearer token required. Only the trip owner can request suggestions.

**Request:**
```json
{
  "interests": ["history", "food", "nature"],
  "budget": "moderate",
  "pace": "relaxed"
}
```
All fields are optional lists/strings. Gemini uses them as prompt context alongside the trip's existing stops.

**Limits (SCRUM-217):**
- `interests` — at most 10 elements, each at most 50 characters
- `budget` — at most 50 characters
- `pace` — at most 50 characters
- The rendered Gemini prompt (interests/budget/pace plus the trip's own stop names) is capped at 8,000 characters as a defensive backstop, since stop count itself isn't bounded by the per-field limits above; exceeding it is a `400` even if every individual field is within its own limit.

**Success (200):**
```json
{
  "tripId": 1,
  "summary": "A 3-day cultural and culinary tour of...",
  "stops": [
    {
      "order": 1,
      "name": "St. Lawrence Market",
      "latitude": 43.6487,
      "longitude": -79.3715,
      "reason": "Historic market with local food vendors — fits your interest in food and history."
    }
  ]
}
```

**Rate limit (SCRUM-173):** Capped per authenticated user at `app.ratelimit.ai-suggest.capacity` requests per `app.ratelimit.ai-suggest.window` (default 10/hour) — see the Rate Limiting section below.

**Errors:**
- 400 — `interests`/`budget`/`pace` violate the limits above (`fieldErrors`), or the rendered prompt exceeds the total size backstop
- 403 — authenticated user is not the trip owner
- 404 — trip not found
- 429 — the caller exceeded their per-user limit on this endpoint (`RateLimitExceededException`), includes a `Retry-After` header
- 502 — Gemini API unreachable (`GeminiClientException`) or returned an unparseable response (`GeminiParsingException`)

**Note:** The `502` on parsing failure is intentional — `SuggestedItinerary` uses `@JsonIgnoreProperties(ignoreUnknown = false)` so unexpected fields in Gemini's response fail loudly rather than being silently dropped. The error message distinguishes between connectivity failure ("AI itinerary service is temporarily unavailable") and parsing failure ("AI itinerary service returned an unreadable response").

---

### POST /api/trips/ai-generate
Sends a free-text prompt to Google Gemini and creates a **brand-new trip** with AI-suggested stops in one call — unlike `ai-suggest` above, this endpoint persists. There is no existing trip to own; the created trip belongs to the authenticated caller.

**Auth:** Bearer token required.

**Request:**
```json
{
  "prompt": "3 days in Kyoto, food and temples, moderate budget",
  "title": "Kyoto Trip"
}
```
`prompt` is required (max 1000 characters). `title` is optional — when omitted, Gemini's own generated title is used instead.

**Success (201):** a full `TripResponse` (same shape as `POST /api/trips`), visibility always `PRIVATE`. Each generated stop's `notes` field is populated with Gemini's stated reason for including it.

**Errors:**
- 400 — `prompt` missing/blank/too long, or the rendered prompt exceeds the 8,000-character backstop
- 422 — Gemini returned zero stops for this prompt (nothing is persisted in this case)
- 429 — the caller exceeded their per-user limit on this endpoint, includes a `Retry-After` header
- 502 — Gemini API unreachable or returned an unparseable response

---

## Trip Export (SCRUM-175/176)

### GET /api/trips/{id}/calendar.ics
Generates a standard `.ics` (RFC 5545 iCalendar) file from a trip's ordered stops — one `VEVENT` per stop.

**Auth:** Bearer token required. Same visibility rule as `GET /api/trips/{id}` — owner sees any trip, non-owners only see `PUBLIC` trips.

**Request:** No body.

**Success (200):**
- `Content-Type: text/calendar`
- `Content-Disposition: attachment; filename="{sanitized-trip-title}.ics"` — the title is stripped to letters/digits/spaces/dashes and capped at 100 chars so it can't inject header syntax or path-unsafe characters.
- Body: a valid `VCALENDAR` with one `VEVENT` per stop:
  - `SUMMARY` — stop name
  - `LOCATION` — stop address (omitted if the stop has no address)
  - `GEO` — stop latitude/longitude
  - `DTSTART`/`DTEND` — from the stop's `dayNumber`/`plannedTime` (SCRUM-244a) when the trip has been scheduled, using `app.schedule.default-visit-duration` (same property route optimization's scheduler uses) as the event length; falls back to an all-day event on the trip's `startDate` (or the export date, if that's also unset) when the stop has no schedule yet.
  - All date-times are written **floating** (no `Z`, no `TZID`) — a stop's `plannedTime` is a destination-local wall-clock time with no known timezone (no lat/lng-to-timezone lookup), so converting it through the server's own timezone would silently produce the wrong hour depending on where the backend happens to be deployed.

**Errors:**
- 404 — trip not found, **or** the trip is `PRIVATE` and requester is not the owner (same 404 either way — `IcsExportService` delegates its ownership/visibility check to `TripService.getTrip`, so it inherits that endpoint's no-403 existence-hiding behavior; this line previously said 403)

All errors return the standard `ApiError` body.

### GET /api/trips/{id}/export/pdf (EXPORT-02)
Generates a formatted PDF itinerary — header (title, start date, stop count, description), an ordered stops table (name/address, day/time schedule, notes), and a best-effort route map snapshot.

**Auth:** Bearer token required. Same visibility rule as `GET /api/trips/{id}`/`.ics` above — owner sees any trip, non-owners only see `PUBLIC` trips; `PdfExportService` delegates to the same `TripService.getTrip` ownership/visibility check.

**Request:** No body.

**Success (200):**
- `Content-Type: application/pdf`
- `Content-Disposition: attachment; filename="{sanitized-trip-title}.pdf"` — same `sanitizeFilename` convention as the `.ics` export above (letters/digits/spaces/dashes, capped at 100 chars), so the two exports never disagree on what a "safe" filename looks like for the same title.
- Body: the PDF. Stops are rendered in `stopOrder`; a stop's schedule column is blank until the trip has been (re-)optimized (same `dayNumber`/`plannedTime` fields as everywhere else, SCRUM-244a).

**The embedded map is best-effort (D-04) — never fails the download.** It is silently omitted (the rest of the PDF still generates normally) when any of the following is true:
- the trip has zero stops (nothing to render);
- the backend's Mapbox access token (`MAPBOX_TOKEN`/`mapbox.access-token`) is unprovisioned;
- the Mapbox Static Images API call fails (network error, non-2xx response) — logged server-side, never surfaced to the client as a failed export;
- the request URL would exceed Mapbox's documented length cap even after falling back from the route-line overlay to a marker-only overlay.

When `Trip.routeGeometry` is present (the trip has been optimized at least once), the map shows the actual route line; otherwise it falls back to plain stop-location pins (D-04).

**Errors:**
- 404 — trip not found, **or** the trip is `PRIVATE` and requester is not the owner (same 404-not-403 convention as every other trip read in this file)

All errors return the standard `ApiError` body.

---

## Rate Limiting (SCRUM-173)

`POST /api/trips/{id}/ai-suggest`, `POST /api/trips/ai-generate`, and `POST /api/trips/{id}/optimize` all call paid/quota-limited external APIs (Gemini, OpenRouteService), so each is capped per authenticated user via an in-memory token bucket (Bucket4j), keyed on the JWT-derived user id — not IP, since multiple users can share an IP (NAT, campus wifi).

`POST /api/auth/login`, `POST /api/auth/register` and `POST /api/auth/refresh` are also capped, via the same Bucket4j mechanism, but keyed on `HttpServletRequest.getRemoteAddr()` instead — there's no JWT yet at that point. Refresh is keyed on the address rather than the token hash for the same reason: an attacker cycling forged token values would otherwise get a fresh bucket per attempt. See `docs/auth.md`'s "Auth rate limiting trust chain" note for how the client IP is derived behind Render's proxy and what's verified vs. assumed about it.

**Limits** (externalized in `application.properties`, tunable without a redeploy):

| Property | Default | Endpoint |
| --- | --- | --- |
| `app.ratelimit.ai-suggest.capacity` / `.window` | 10 / `1h` | `POST /api/trips/{id}/ai-suggest` |
| `app.ratelimit.ai-generate.capacity` / `.window` | 5 / `1h` | `POST /api/trips/ai-generate` |
| `app.ratelimit.optimize.capacity` / `.window` | 20 / `1h` | `POST /api/trips/{id}/optimize` |
| `app.ratelimit.login.capacity` / `.window` | 10 / `1h` | `POST /api/auth/login` |
| `app.ratelimit.register.capacity` / `.window` | 5 / `1h` | `POST /api/auth/register` |
| `app.ratelimit.refresh.capacity` / `.window` | 60 / `1h` | `POST /api/auth/refresh` |

Exceeding the limit returns `429 Too Many Requests` with the standard `ApiError` body and a `Retry-After` header (seconds until the next token is available). The counter resets in full once the configured window elapses (Bucket4j "intervally" refill — tokens jump back to full capacity at the window boundary, not a continuous trickle).

**Note:** this is a single-instance, in-memory limiter — it resets on restart and isn't shared across multiple backend instances. A multi-instance deployment would need a distributed Bucket4j backend (e.g. Redis) instead.

---

## Photo Upload — Cloudinary (SCRUM-152/153)

Direct-to-Cloudinary upload: the backend only issues a signature and persists the resulting URL — no binary ever passes through our backend.

### POST /api/stops/{stopId}/photo-signature
Owner-only. Issues Cloudinary signed upload parameters.

**Success (200):**
```json
{
  "cloudName": "string",
  "apiKey": "string",
  "timestamp": 0,
  "signature": "string",
  "uploadParams": {}
}
```
**Errors:** 403 (not owner), 404 (stop not found)

### POST /api/stops/{stopId}/photos
Owner-only. Persists a photo reference after the client has uploaded directly to Cloudinary using the signature above.

**Request:**
```json
{
  "url": "string",
  "cloudinaryPublicId": "string",
  "caption": "string"
}
```
**Success (201):**
```json
{
  "id": 1,
  "stopId": 1,
  "url": "string",
  "cloudinaryPublicId": "string",
  "caption": "string",
  "createdAt": "ISO-8601 datetime"
}
```
**Errors:** 403 (not owner), 404 (stop not found), 400 (validation — `url` required, max 2048 chars; `cloudinaryPublicId` max 255 chars; `caption` max 500 chars — SCRUM-417)

### GET /api/stops/{stopId}/photos
Owner sees any stop's photos; non-owner only if the stop's parent trip is `PUBLIC`. Intentionally **not** paginated (see REF-21 note under `GET /api/trips`) — there is no application-level cap on photos per stop today.
**Success (200):** array of the photo object shape above.
**Errors:** 404 (stop not found, **or** the parent trip is `PRIVATE` and requester is not the owner — same 404 either way, fixed under SCRUM-274: a 403 here would have confirmed the stop id exists, making this an existence oracle for stops on other people's private trips, same convention as `GET /api/trips/{id}` and the clone/like endpoints above. The other stop-photo endpoints below are owner-only writes, a different case, and keep 403.)

### DELETE /api/stops/{stopId}/photos/{photoId}
Owner-only.
**Success (204):** no body.
**Errors:** 403 (not owner), 404 (photo not found, or belongs to a different stop — same 404 either way so existence isn't leaked)

---

## Standard Error Shape — CORRECTED (matches ApiError as of REF-10)

The shape below in the original doc is now stale. Actual response body:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "string",
  "path": "/api/trips/5",
  "timestamp": "ISO-8601 UTC, e.g. 2026-07-10T20:36:04.123Z",
  "fieldErrors": [
    { "field": "title", "message": "must not be blank" }
  ]
}
```
Notes:
- `timestamp` is `Instant`, always UTC with trailing `Z` (not `LocalDateTime` — confirmed in REF-10).
- `fieldErrors` is an **array** of `{field, message}` objects, not a map — only present on 400 validation errors, `null`/omitted otherwise.
- `error` is the HTTP reason phrase (e.g. `"Not Found"`, `"Forbidden"`), separate from `message`.

### Additional status codes (SCRUM-213 / AUDIT-04)

`GlobalExceptionHandler` also covers these client-error conditions, previously falling through to a 500:

| Condition | Status | `message` |
| --- | --- | --- |
| Malformed JSON request body | 400 | `"Malformed request body"` — the raw parse error is never echoed back, only logged server-side |
| Non-numeric value for a numeric path variable (e.g. `GET /api/trips/abc`) | 400 | `"Invalid value for parameter '<name>'"` |
| Database constraint violation not already mapped to a specific 409 (e.g. `DuplicateEmailException`) | 409 | `"The request conflicts with existing data"` — no constraint name, table name, or SQL fragment is ever included in the response body |
| Unsupported HTTP method on an existing route | 405 | the framework's standard method-not-allowed message |
| No matching route | 404 | `"No matching route for this request"` |
| Request body over the global size cap, discovered mid-read rather than via `Content-Length` (SCRUM-417) | 413 | `"Request body exceeds the maximum allowed size"` |

All of the above log at `WARN`, never `ERROR` — they are ordinary client-error conditions, not application bugs.
