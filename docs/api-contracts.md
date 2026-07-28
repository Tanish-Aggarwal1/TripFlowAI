# API Contracts

Living document. Add a new section per epic as endpoints are built. Update if a contract changes — announce the change in team chat when it does.

## Auth (Sprint 1)

### POST /api/auth/register
**Request:**
```json
{
  "email": "string",
  "password": "string",
  "displayName": "string"
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
  ]
}
```
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
    "notes": "string"
  }
]
```

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
      "notes": "string"
    }
  ],
  "createdAt": "2026-07-20T15:30:00Z",
  "updatedAt": "2026-07-20T15:31:00Z",
  "routeGeometry": "encoded_polyline_string"
}
```

**Errors:**
- 403 — authenticated user is not the trip owner
- 404 — trip not found
- 422 — trip has fewer than 2 stops (nothing to optimize)
- 502 — OpenRouteService is unreachable or returned a server error (`OrsClientException`)
- 429 — ORS rate limit exceeded (`OrsRateLimitException`)

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

**Errors:**
- 400 — `interests`/`budget`/`pace` violate the limits above (`fieldErrors`), or the rendered prompt exceeds the total size backstop
- 403 — authenticated user is not the trip owner
- 404 — trip not found
- 502 — Gemini API unreachable (`GeminiClientException`) or returned an unparseable response (`GeminiParsingException`)

**Note:** The `502` on parsing failure is intentional — `SuggestedItinerary` uses `@JsonIgnoreProperties(ignoreUnknown = false)` so unexpected fields in Gemini's response fail loudly rather than being silently dropped. The error message distinguishes between connectivity failure ("AI itinerary service is temporarily unavailable") and parsing failure ("AI itinerary service returned an unreadable response").

---

## Photo Upload — Cloudinary (SCRUM-66, planned)

*Not yet implemented. Endpoint contract will be added here once SCRUM-66 lands.*

**Planned flow:**
1. `POST /api/trips/{tripId}/stops/{stopId}/photos/upload-params` — backend issues Cloudinary signed upload parameters.
2. Client uploads directly to Cloudinary using the signed params (no binary data passes through our backend).
3. Client sends the resulting Cloudinary URL back via `POST /api/trips/{tripId}/stops/{stopId}/photos` to persist the reference.

**Prerequisite:** A `Photo` entity and Flyway migration (not yet created) to store the Cloudinary URL against a stop.

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
