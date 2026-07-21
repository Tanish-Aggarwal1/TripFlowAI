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

## Trips & Stops (SCRUM-52)

### GET /api/trips
Returns the authenticated user's trips.
**Success (200):**
```json
[
  {
    "id": 1,
    "title": "string",
    "description": "string",
    "tags": ["string"],
    "visibility": "PRIVATE | PUBLIC",
    "status": "string",
    "createdAt": "ISO-8601 datetime",
    "updatedAt": "ISO-8601 datetime",
    "stops": [ /* StopResponse[], see below */ ],
    "routeGeometry": "string"
  }
]
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