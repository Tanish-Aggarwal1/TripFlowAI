# TripFlowAI Logging Standard

Applies to all backend code. Reused verbatim in the SDP coding-standards section.

## Framework

- SLF4J via `@Slf4j` (Lombok). No `System.out`, no `printStackTrace()`, no `e.printStackTrace()`.
- One logger per class, named `log` (Lombok default).

## Levels

| Level | Use for | Examples |
|---|---|---|
| `ERROR` | Unhandled exceptions and unexpected failures a human should investigate. Always include the throwable. | 500 responses in `GlobalExceptionHandler#handleGeneric`, database connection loss, external API 5xx after retries. |
| `WARN` | Handled exceptional cases: client errors (4xx), auth failures, rejected inputs, expected but abnormal states. | Invalid JWT, validation failure, duplicate email, ORS quota exceeded. |
| `INFO` | State changes worth auditing. One line per business operation. | User registered, user logged in, trip created/updated/deleted, stop added/updated/deleted, route optimization run. |
| `DEBUG` | Diagnostic detail for reproducing issues. Off in production. | JWT authenticated for user, ownership check failed, resolved a shared Place from cache, external API request/response summaries. |
| `TRACE` | Not used. |

## What NEVER to log

- Passwords, password hashes, or any raw credential input.
- JWT tokens (any part — header, payload, signature).
- `Authorization` header values.
- API keys or secrets (ORS, Gemini, Cloudinary, Mapbox).
- Full request/response bodies containing user PII.
- Session identifiers or cookies.

If in doubt, log an identifier (userId, tripId) instead of the value.

## Message format

Use SLF4J parameterized messages, never string concatenation:

```java
log.info("Trip created id={} ownerId={} stops={}", saved.getId(), ownerId, saved.getStops().size());   // yes
log.info("Trip created id=" + saved.getId());                                                          // no
```

Include the throwable as the last argument for stack traces:

```java
log.error("500 Internal Server Error on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
```

Key-value style (`key=value`) makes messages greppable and future-friendly for structured log parsers.

## Exception handling contract

- `GlobalExceptionHandler` is the only place that logs unhandled exceptions.
- Never `catch (Exception e) {}` silently. If you must swallow, log at `warn` with a rationale in the comment.
- Services throw domain exceptions (`ResourceNotFoundException`, `ForbiddenException`, `InvalidCredentialsException`, …). The handler translates and logs.
- Filters that swallow exceptions (auth filters, request-context filters) log at `warn` and let the security chain reject the request.

## Contextual data (MDC)

Not currently used. Planned for a future ticket: request ID, user ID, and trip ID injected into MDC by a servlet filter for correlation. Until then, include identifiers directly in log messages.

## Verification

- `grep -R "System\.out" backend/src` should return zero results.
- `grep -R "printStackTrace" backend/src` should return zero results.
- A local 500 trigger (e.g. force an unhandled exception in a controller) should produce a single `ERROR` line with a stack trace in the console.