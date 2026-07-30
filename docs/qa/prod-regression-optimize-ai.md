# Prod Regression — Route Optimization + AI Itinerary (SCRUM-74b)

Run against the deployed production environment before the Aug 6 presentation. Fill in **Actual** and **Result** as you go.

Tester: __________  Date: __________  Backend build/commit: __________

Prerequisite: a logged-in test account with at least one trip that has ≥2 stops with valid coordinates (`POST /api/trips/{id}/optimize` requires this — fewer than 2 stops returns `422`).

## Route Optimization — `POST /api/trips/{id}/optimize`

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 1 | Optimize — 3 stops | Create/use a trip with 3 stops, call optimize | `200`, stops reordered by shortest travel time, `routeGeometry` populated, each stop gets `dayNumber`/`plannedTime` | | |
| 2 | Optimize — 5 stops | Same, 5 stops | `200`, same shape, correct reorder | | |
| 3 | Optimize — 10 stops | Same, 10 stops | `200`, same shape, correct reorder, no timeout | | |
| 4 | Optimize — fewer than 2 stops | Trip with 0 or 1 stop | `422` | | |
| 5 | Optimize — non-owner | Call as a user who doesn't own the trip | `403` | | |
| 6 | Optimize — trip not found | Call with a bogus trip id | `404` | | |
| 7 | Optimize — rate limit | Call >20 times within an hour on the same account (default `app.ratelimit.optimize.capacity`) | `429` with `Retry-After` header once exceeded | | |
| 8 | ORS unreachable/5xx/slow | Hard to force in prod — note if ORS has an outage during testing, or simulate via a bad `ORS_API_KEY` in a scratch env | `502` (`OrsClientException`), never a raw 500, and the frontend shows a toast rather than a blank failure | | |

## AI Itinerary — `POST /api/trips/{id}/ai-suggest`

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 9 | Happy path — destination profile 1 | Request suggestions for a trip in one city/region, with `interests`/`budget`/`pace` set | `200`, `summary` + `stops[]` with `order`/`name`/`latitude`/`longitude`/`reason` | | |
| 10 | Happy path — destination profile 2 | Same, different city/region (pick something geographically distinct from #9) | `200`, plausible suggestions for that location | | |
| 11 | Accept multiple suggestions in sequence | From the suggestion cards UI, accept 2-3 suggested stops one after another | Each accepted stop persists via `POST /api/trips/{id}/stops`; stop list reflects all of them without a full page reload | | |
| 12 | Field limit violation | `interests` with 11+ elements, or one interest >50 chars | `400` with `fieldErrors` | | |
| 13 | Rate limit | Call >10 times within an hour on the same account (default `app.ratelimit.ai-suggest.capacity`) | `429` with `Retry-After` header | | |
| 14 | Gemini unreachable / malformed response | Hard to force in prod — note if Gemini has an outage during testing | `502`, message distinguishes connectivity vs. parsing failure per `docs/api-contracts.md` | | |
| 15 | Non-owner / not found | Call as non-owner, or with a bogus trip id | `403` / `404` respectively | | |

## Known-issue log

| # | Description | Severity | Workaround / notes |
|---|---|---|---|
| | | | |
