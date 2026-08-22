---
phase: 2
slug: exports-completion-search
status: verified
threats_open: 0
asvs_level: 1
created: 2026-08-22
---

# Phase 2 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| browser -> `GET /api/trips/{id}/export/pdf` | Untrusted `id` path variable and an authenticated `UserPrincipal` cross here; the requester may not own the trip. | trip id, principal |
| backend -> Mapbox Static Images API | Outbound request carrying a backend secret and trip-derived (server-generated) geometry. | Mapbox access token, route geometry |
| backend -> `Content-Disposition` response header | A user-authored trip title crosses into HTTP header syntax. | trip title |
| build -> Maven Central | A new third-party dependency (`openpdf`) enters the build. | dependency artifact |
| browser -> `GET /api/trips` | An authenticated `UserPrincipal` crosses here; the response must contain only this user's trips and only this user's progress. | principal, completion data |
| owner-list query <-> discovery-feed query | Two response shapes served from the same table by different queries; the boundary between them is what keeps one user's progress out of another user's feed. | completion percentage |
| browser -> `GET /api/trips?search=...&status=...` | Free-text and typed filter values from an untrusted client reach query construction. | search text, filter params |
| repository -> Postgres (native SQL) | The only place in this phase where request-derived text meets SQL. | search pattern, filter values |
| requester <-> other users' trips | The owner-scope predicate is the whole boundary; a leak here is a cross-account disclosure. | trip existence/count |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-02-01 | Information Disclosure | `PdfExportService.exportPdf` | high | mitigate | First statement delegates to `TripService.getTrip(tripId, requesterId)` — owner-or-PUBLIC check, 404 (not 403) for a non-owner's PRIVATE trip. Confirmed via code read: `PdfExportService.java` `exportPdf()` first line. `PdfExportServiceTest#exportPdf_delegatesOwnershipCheckToTripService` asserts the delegation call. | closed |
| T-02-02 | Information Disclosure | `MapboxProperties`, `MapboxClient` logging | high | mitigate | `toString()` masks `accessToken` via `config/SecretMask.mask(...)`; URL-downgrade WARN logs stop count, never the request URI. Confirmed via code read: `MapboxProperties.toString()`. `MapboxClientTest#mapboxProperties_toString_doesNotContainTheFullAccessToken` asserts. | closed |
| T-02-03 | Tampering / SSRF-adjacent | `MapboxClient.staticSnapshot` | medium | mitigate | Host and path fixed by `MapboxProperties.baseUrl`/config and literal path template — neither derivable from request input; only request-influenced content is server-generated `routeGeometry`/numeric coordinates. Confirmed via code read: `MapboxClient.requestPath()`. | closed |
| T-02-04 | Tampering | `Content-Disposition` header construction | medium | mitigate | PDF filename built exclusively through existing `TripExportController.sanitizeFilename` (strips outside `[a-zA-Z0-9 -]`, caps 100 chars) — no second sanitizer (D-05). Confirmed via code read: `TripExportController.java:63`. | closed |
| T-02-05 | Denial of Service | `MapboxClient` request assembly | low | mitigate | 8,000-char URL threshold downgrades the overlay before sending; independent 5s/10s connect/read timeouts. Confirmed via code read (`MAX_URL_LENGTH`) and `MapboxClientTest#staticSnapshot_overLengthGeometry_fallsBackToMarkerOnlyOverlay` (passing). | closed |
| T-02-SC | Tampering (supply chain) | `com.github.librepdf:openpdf` dependency | high | mitigate | RESEARCH.md's Package Legitimacy Audit verdict OK — verified against Maven Central's solrsearch API and jar class listing; version pinned exactly, no range. | closed |
| T-02-06 | Information Disclosure | `TripSummaryResponse` shared by `findSummariesByUserId`, `findSummariesByVisibility`, `searchPublicTrips` | high | mitigate | D-08 DTO fork: `TripOwnerSummaryResponse` backs only the owner list; `TripSummaryResponse` stays byte-for-byte unchanged for both discovery paths — enforced structurally at the query, plus a `TripRepositoryIT` 8-component tripwire. Independently confirmed this session via code read (`TripSummaryResponse.java` still exactly 8 components) and `02-VERIFICATION.md` plan-level truth #5. | closed |
| T-02-07 | Information Disclosure | `TripController.listTrips` | high | mitigate | Scope is `principal.userId()` from the authenticated principal, threaded into `findSummariesByUserId`'s `WHERE t.user.id = :userId` — no user id from request input. Confirmed via code read: `TripController.listTrips()`, `principal.userId()` passed to `tripService.listTrips`/`searchOwnedTrips`. | closed |
| T-02-08 | Denial of Service | completion computation | low | accept | Correlated subquery bounded by page size (max 20 rows); accepted — an index for this scale would be premature. Documented in `02-03-PLAN.md`'s own threat register. | closed |
| T-02-09 | Tampering | `TripSearchRepositoryImpl.searchOwnedTrips` native queries | high | mitigate | Query text is a static string constant; every user-derived value enters via `.setParameter(...)`. Independently confirmed this session via direct grep: 4 `createNativeQuery` calls, 15 `setParameter` calls, zero string concatenation. | closed |
| T-02-10 | Information Disclosure | `searchOwnedTrips` owner scoping | high | mitigate | `t.user_id = :userId` in the WHERE clause of both the id query and the count query, sourced from `principal.userId()`, never request input. Independently confirmed this session via direct grep (lines 141, 164 of `TripSearchRepositoryImpl.java`). `TripSearchRepositoryIT#searchOwnedTrips_anotherUsersPublicTripMatching_returnsNothing` asserts. | closed |
| T-02-11 | Information Disclosure | D-08 boundary under search | high | mitigate | `searchOwnedTrips` re-fetches into `TripOwnerSummaryResponse`; `searchPublicTrips` keeps `TripSummaryResponse` untouched — confirmed via code read of both methods in `TripSearchRepositoryImpl.java`. Plan 02-03's record-component guard test remains the tripwire. | closed |
| T-02-12 | Denial of Service | filter/search query cost | medium | mitigate | Id query is `LIMIT`/`OFFSET`-bounded by `Pageable` before refetch; endpoint behind default-deny `SecurityConfig` (authenticated-only). Confirmed via code read: `matchingOwnedIds` query text includes `LIMIT :limit OFFSET :offset`. | closed |
| T-02-13 | Denial of Service / Input Validation | `TripController.listTrips` param binding | low | mitigate | `status`/`visibility` bind to typed enums, dates to `LocalDate`, `durationDays` to `Integer` — Spring rejects malformed values at binding time into the canonical 400 `ApiError` path, not hand-parsed. Confirmed via code read: `TripController.listTrips()` method signature. | closed |

*Status: open · closed · open — below `high` threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above `workflow.security_block_on` (`high`) count toward `threats_open`*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-02-01 | T-02-08 | Completion count is a correlated subquery bounded to a max-20-row page; adding an index at current scale is premature optimization, not a real DoS exposure. | GSD planner (02-03-PLAN.md, at plan time) | 2026-08-21 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-22 | 14 | 14 | 0 | Claude (gsd-secure-phase, L1 grep-depth per short-circuit rule — all 3 plans authored their threat register at plan time, `asvs_level: 1`) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-08-22
