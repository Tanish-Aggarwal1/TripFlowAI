# API Conventions

## Error Response Contract

All errors from the API (validation, not-found, forbidden, unexpected) return this shape:

```json
{
  "timestamp": "2026-07-03T14:20:00",
  "status": 404,
  "error": "Not Found",
  "message": "Trip not found",
  "path": "/api/trips/99",
  "fieldErrors": null
}
```

For validation errors (400), `fieldErrors` is populated:

```json
{
  "timestamp": "2026-07-03T14:20:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/trips",
  "fieldErrors": [
    { "field": "title", "message": "must not be blank" }
  ]
}
```

| Field | Type | Notes |
|---|---|---|
| timestamp | string (ISO 8601) | server time of the error |
| status | int | HTTP status code |
| error | string | HTTP reason phrase |
| message | string | human-readable error message |
| path | string | request URI |
| fieldErrors | array \| null | populated only on 400 validation errors |

Status codes currently mapped:
- `404` — resource not found (`ResourceNotFoundException`)
- `403` — forbidden (`ForbiddenException`)
- `400` — bean validation failure
- `500` — unhandled exception (generic fallback)