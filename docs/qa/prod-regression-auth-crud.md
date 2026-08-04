# Prod Regression — Auth + Trip/Stop CRUD (SCRUM-74a)

Run against the deployed production environment before the Aug 6 presentation. Fill in **Actual** and **Result** as you go; leave blank cells for anything not yet run.

Tester: Tanish (scripted via curl)  Date: 2026-08-04  Backend build/commit: main @ 7f55a3c (post-#191)

## Setup

- Deployed frontend URL: https://tripflowai-frontend.onrender.com/
- Deployed backend URL (for direct `curl`/Postman checks): https://tripflowai.onrender.com
- Cold start note: Render free tier spins the backend down when idle — first request after idle took ~90s (`/actuator/health`). **Hit the health endpoint a few minutes before the demo starts** so it's warm.
- Used the existing `tanish@gmail.com` account for read/CRUD scenarios needing an owner with pre-existing data (per Tanish's instruction), plus a disposable `qa-regression-tester@example.com` account for signup and non-owner/authorization scenarios. All test trips/stops created during this pass were deleted afterward — `tanish@gmail.com`'s original 3 trips (wasaga, Muskoka, Montreal) are untouched.

## Auth

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 1 | Signup — happy path | `POST /api/auth/register` with valid `email`/`password`/`username` | `201`, body has `token` + `expiresAt` | `201`, token + `userId:4` + `expiresAt` returned | PASS |
| 2 | Signup — invalid email format | Register with `email: "not-an-email"` | `400`, `fieldErrors` includes `email` | `400`, `fieldErrors:[{field:"email","must be a well-formed email address"}]` | PASS |
| 3 | Signup — duplicate email | Register twice with the same email | Second attempt `409` | `409`, "Email already registered: tanish@gmail.com" | PASS |
| 4 | Login — happy path | `POST /api/auth/login` with the account from #1 | `200`, `token` + `expiresAt` | `200`, token + `expiresAt` | PASS |
| 5 | Login — wrong password | Login with correct email, wrong password | `401` | `401`, "Invalid email or password" | PASS |
| 6 | Protected endpoint with valid token | `GET /api/trips` with `Authorization: Bearer <token>` from #4 | `200` | `200`, full trip list returned | PASS |
| 7 | Protected endpoint, no token | `GET /api/trips` with no `Authorization` header | `401` | `401`, "Authentication required" | PASS |
| 8 | Protected endpoint, malformed/expired token | `GET /api/trips` with a garbage or expired token | `401` | `401`, "Authentication required" | PASS |
| 9 | Session persists across refresh | Log in on the frontend, refresh the page | Still logged in (token persisted client-side, no forced re-login) | Not testable via curl — needs a manual browser check | **NOT RUN — manual step needed** |

## Trip CRUD

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 10 | Create trip | `POST /api/trips` with title + ≥1 stop | `201`, trip object returned | `201`, full trip incl. stop | PASS |
| 11 | View trip | `GET /api/trips/{id}` as owner | `200`, full trip incl. `stops` | `200` | PASS |
| 12 | Edit trip | `PUT /api/trips/{id}` — change title/description/tags/visibility/stops | `200`, updated trip reflects changes | `200`, title/visibility updated, stops replaced | PASS |
| 13 | Delete trip | `DELETE /api/trips/{id}` as owner | `204`, then `GET` on same id → `404` | `204`, then `404` "Trip not found: 6" | PASS |
| 14 | List trips paginated | `GET /api/trips?page=0&size=20` | `200`, `content` + `page` block | `200`, correct shape | PASS |

## Stop CRUD

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 15 | Add stop | `POST /api/trips/{tripId}/stops` | `201`, stop appended at next `stopOrder` | `201`, but response body has **`"id":null`** — the generated stop id isn't populated in the immediate POST response (confirmed correct on a follow-up `GET`, so it's a response-mapping issue, not a persistence bug) | **FAIL (see known issues)** |
| 16 | Reorder stops | `PUT /api/trips/{id}` with reordered `stops[]` | Stop order persists after reload | `200`, order swapped correctly on refetch | PASS |
| 17 | Delete stop | `DELETE /api/trips/{tripId}/stops/{stopId}` | `204`, remaining stops renumbered (no gap in `stopOrder`) | `204`, remaining stop correctly at `stopOrder:0` | PASS |

## Authorization boundaries

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 18 | 401 on unauthenticated protected request | Any `/api/trips/**` call with no token | `401` via `JsonAuthenticationEntryPoint` | `401` | PASS |
| 19 | 403 on another user's PRIVATE trip | `GET` tanish's PRIVATE "Montreal" trip as the qa-tester account | `403` | `403`, "You do not have access to this trip" | PASS |
| 20 | Non-owner cannot edit/delete | `PUT`/`DELETE` on Montreal as non-owner | `403` | Both `403` | PASS |

## Known-issue log

Record anything that fails here with severity (blocker / major / minor) and a one-line description — feeds into SCRUM-172's consolidated sign-off.

| # | Description | Severity | Workaround / notes |
|---|---|---|---|
| 1 | `POST /api/trips/{tripId}/stops` returns the new stop with `id: null` in the response body (data itself is fine — a follow-up `GET` shows the correct id) | Minor | Frontend `onStopAdded`/`onSuggested` flows key off `stop.id` for `@for` tracking — a stop added mid-session could momentarily track as `null`. Doesn't block the demo (single-add flows work visually), but worth a real fix before the AI-suggest-accept flow gets hammered live. Likely a response-mapping timing issue in `StopService`/`StopMapper` (entity not re-fetched after save before mapping). |
