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
  "expiresAt": "ISO-8601 datetime"
}
```
**Errors:**
- 409 — email already registered
- 400 — validation failure (see standard error shape below)

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


## Auth Header
Protected endpoints require: `Authorization: Bearer <token>`

Missing, malformed, or expired token → `401 Unauthorized` with the standard `ApiError` body (see below). Valid token but not authorized for the resource (e.g. non-owner) → `403 Forbidden`, same body shape. See `docs/auth.md` for the full breakdown of which mechanism handles which case.

---

## Trips & Stops (SCRUM-52)

### GET /api/trips (paginated — REF-21)
Returns a page of the authenticated user's trips as a card-sized projection — no `stops` array, just `stopCount` — so list reads never need the collection fetch join used by `GET /api/trips/{id}`. This is the canonical paging contract for all future list endpoints: accept Spring `Pageable` (`?page=&size=&sort=`), return this same paged shape.

**Query params:** `page` (0-indexed, default 0), `size` (default 20), `sort` (default `createdAt,desc`, e.g. `?sort=title,asc`).

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
      "coverPhotoUrl": null
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
`coverPhotoUrl` is always `null` until the Cloudinary photo feature (SCRUM-66) lands. Use `GET /api/trips/{id}` for the full itinerary including `stops`.
```

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

**Success (201):** single trip object, same shape as GET list item.
**Errors:**
- 400 — validation failure

**Field limits (SCRUM-212 / AUDIT-03)** — enforced by Bean Validation, mirroring the DB column widths and coordinate domain exactly. A violation returns 400 with a `fieldErrors` entry naming the field, never a 500 from a downstream DB constraint:

| Field | Limit |
| --- | --- |
| `title` | max 150 chars |
| `tags` | max 20 elements, each max 50 chars |
| `stops[].name` | max 200 chars |
| `stops[].address` | max 300 chars |
| `stops[].externalPlaceId` | max 150 chars |
| `stops[].latitude` | -90.0 to 90.0 |
| `stops[].longitude` | -180.0 to 180.0 |

Same limits apply to `PUT /api/trips/{id}` and the nested stop endpoints (`POST`/`PUT /api/trips/{tripId}/stops[/{stopId}]`), which share `CreateStopRequest`/`UpdateStopRequest`.

### GET /api/trips/{id}
Owner sees any trip; non-owner only sees `PUBLIC` trips.
**Success (200):** single trip object.
**Errors:**
- 404 — trip not found
- 403 — private trip, requester is not the owner

### PUT /api/trips/{id}
Full itinerary replace — metadata + stops in one call. Existing stops not present in the request are deleted; their `Place` rows survive if referenced elsewhere.
**Request:** same shape as POST.
**Success (200):** updated trip object.
**Errors:**
- 404 — trip not found
- 403 — requester is not the owner
- 400 — validation failure

### DELETE /api/trips/{id}
**Success (204):** no body.
**Errors:**
- 404 — trip not found
- 403 — requester is not the owner

### GET /api/trips/{tripId}/stops
Owner-only (no public read on this sub-resource — use GET /api/trips/{id} for public itinerary viewing).
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

**Success (200):** Returns the full `TripResponse` with stops reordered by optimized `orderIndex` and `routeGeometry` populated with an encoded polyline string.
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
      "orderIndex": 0,
      "notes": "string",
      "dayNumber": 1,
      "plannedTime": "09:00:00",
      "stopType": "SIGHTSEEING"
    }
  ],
  "createdAt": "2026-07-20T15:30:00Z",
  "updatedAt": "2026-07-20T15:31:00Z",
  "routeGeometry": "encoded_polyline_string"
}
```

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
- 403 — private trip, requester is not the owner
- 404 — trip not found

All errors return the standard `ApiError` body.

---

## Rate Limiting (SCRUM-173)

`POST /api/trips/{id}/ai-suggest`, `POST /api/trips/ai-generate`, and `POST /api/trips/{id}/optimize` all call paid/quota-limited external APIs (Gemini, OpenRouteService), so each is capped per authenticated user via an in-memory token bucket (Bucket4j), keyed on the JWT-derived user id — not IP, since multiple users can share an IP (NAT, campus wifi).

**Limits** (externalized in `application.properties`, tunable without a redeploy):

| Property | Default | Endpoint |
| --- | --- | --- |
| `app.ratelimit.ai-suggest.capacity` / `.window` | 10 / `1h` | `POST /api/trips/{id}/ai-suggest` |
| `app.ratelimit.ai-generate.capacity` / `.window` | 5 / `1h` | `POST /api/trips/ai-generate` |
| `app.ratelimit.optimize.capacity` / `.window` | 20 / `1h` | `POST /api/trips/{id}/optimize` |

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
**Errors:** 403 (not owner), 404 (stop not found), 400 (validation — `url` required)

### GET /api/stops/{stopId}/photos
Owner sees any stop's photos; non-owner only if the stop's parent trip is `PUBLIC`.
**Success (200):** array of the photo object shape above.
**Errors:** 403 (private trip, requester not owner), 404 (stop not found)

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

All of the above log at `WARN`, never `ERROR` — they are ordinary client-error conditions, not application bugs.
