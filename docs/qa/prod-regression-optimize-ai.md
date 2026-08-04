# Prod Regression — Route Optimization + AI Itinerary (SCRUM-74b)

Run against the deployed production environment before the Aug 6 presentation. Fill in **Actual** and **Result** as you go.

Tester: Claude (via curl, on Tanish's behalf)  Date: 2026-08-04  Backend build/commit: main @ 7f55a3c (post-#191)

Prerequisite: a logged-in test account with at least one trip that has ≥2 stops with valid coordinates (`POST /api/trips/{id}/optimize` requires this — fewer than 2 stops returns `422`).

## Route Optimization — `POST /api/trips/{id}/optimize`

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 1 | Optimize — 3 stops | Create/use a trip with 3 stops, call optimize | `200`, stops reordered by shortest travel time, `routeGeometry` populated, each stop gets `dayNumber`/`plannedTime` | `200`, real `routeGeometry` LineString returned via live ORS, `dayNumber:1`, `plannedTime` sequenced 09:00/10:00/11:01 | PASS |
| 2 | Optimize — 5 stops | Same, 5 stops | `200`, same shape, correct reorder | **Not run** — skipped to conserve ORS free-tier quota (500 req/day) ahead of rehearsal; 3-stop pass exercises the same code path | NOT RUN |
| 3 | Optimize — 10 stops | Same, 10 stops | `200`, same shape, correct reorder, no timeout | **Not run** — same reason as #2 | NOT RUN |
| 4 | Optimize — fewer than 2 stops | Trip with 0 or 1 stop | `422` | `422`, "Trip must have at least 2 stops to optimize (tripId=8)" | PASS |
| 5 | Optimize — non-owner | Call as a user who doesn't own the trip | `403` | `403`, "You do not have access to this trip" | PASS |
| 6 | Optimize — trip not found | Call with a bogus trip id | `404` | `404`, "Trip not found: 999999" | PASS |
| 7 | Optimize — rate limit | Call >20 times within an hour on the same account (default `app.ratelimit.optimize.capacity`) | `429` with `Retry-After` header once exceeded | **Not run** — would burn most of the account's hourly quota right before rehearsal; already covered by `RouteOptimizationControllerIT`/rate-limiter unit tests in CI | NOT RUN (covered in CI) |
| 8 | ORS unreachable/5xx/slow | Hard to force in prod | `502` (`OrsClientException`), never a raw 500, toast on frontend | **Not run** — ORS was reachable throughout this pass (see #1); covered by `RouteOptimizationServiceTest`/CI for the failure path | NOT RUN (covered in CI) |

## AI Itinerary — `POST /api/trips/{id}/ai-suggest`

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 9 | Happy path — destination profile 1 | Request suggestions for a trip in one city/region, with `interests`/`budget`/`pace` set | `200`, `summary` + `stops[]` with `order`/`name`/`latitude`/`longitude`/`reason` | **`502` "AI itinerary service is temporarily unavailable"** — reproduced twice | **FAIL — BLOCKER** |
| 10 | Happy path — destination profile 2 | Same, different city/region | `200`, plausible suggestions | Not run — blocked by #9 | BLOCKED |
| 11 | Accept multiple suggestions in sequence | Accept 2-3 suggested stops one after another | Each persists via `POST /api/trips/{id}/stops`, no full reload | Not run — blocked by #9 | BLOCKED |
| 12 | Field limit violation | `interests` with 11+ elements | `400` with `fieldErrors` | `400`, `fieldErrors:[{field:"interests","at most 10 interests are allowed"}]` — validated *before* the Gemini call, so this passes independent of #9's outage | PASS |
| 13 | Rate limit | Call >10 times within an hour | `429` with `Retry-After` header | Not run — would consume real quota; covered in CI (`AiControllerIT.suggestItinerary_exceedsRateLimit_...`) | NOT RUN (covered in CI) |
| 14 | Gemini unreachable / malformed response | Note if Gemini has an outage during testing | `502`, message distinguishes connectivity vs. parsing | **This is exactly what happened at #9** — got the connectivity-failure message ("AI itinerary service is temporarily unavailable"), correctly not the parsing-failure message. The error *mapping* is working correctly; the underlying Gemini connectivity in prod is not. | PASS (mapping) / see blocker above |
| 15 | Non-owner / not found | Call as non-owner, or bogus trip id | `403` / `404` | Non-owner → `403` confirmed. Not-found not separately run (same guard as #6/optimize). | PASS |

### Also tested (not in original scope, new since SCRUM-256) — `POST /api/trips/ai-generate`
Same Gemini dependency as `ai-suggest`. Reproduced the identical `502 "AI itinerary service is temporarily unavailable"` on a fresh prompt, confirming this is a shared root cause across both AI endpoints, not specific to one code path.

## Known-issue log

| # | Description | Severity | Workaround / notes |
|---|---|---|---|
| 1 | **Both `POST /api/trips/{id}/ai-suggest` and `POST /api/trips/ai-generate` return `502` on every attempt in prod** ("AI itinerary service is temporarily unavailable"), reproduced 3x across both endpoints. AI itinerary generation is a headline demo feature (per `docs/ajf-module-a.md` presentation notes) — this blocks that entire demo path as of this test run. | **BLOCKER** | Root cause not yet confirmed from outside the deploy — likely candidates: `GEMINI_API_KEY` missing/invalid/expired in Render's env vars for the backend service, Gemini API quota exhausted, or Render's egress can't reach `generativelanguage.googleapis.com`. Route optimization (ORS, a similarly-shaped external integration) works fine in the same environment, which points at Gemini-specific config rather than a general outbound-network problem. **Action: check Render dashboard env vars for `GEMINI_API_KEY`, and check Render service logs for the underlying `GeminiClientException` cause (connect vs. read timeout vs. 4xx from Gemini) before rehearsal.** |
