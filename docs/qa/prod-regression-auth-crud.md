# Prod Regression — Auth + Trip/Stop CRUD (SCRUM-74a)

Run against the deployed production environment before the Aug 6 presentation. Fill in **Actual** and **Result** as you go; leave blank cells for anything not yet run.

Tester: __________  Date: __________  Backend build/commit: __________

## Setup

- Deployed frontend URL: __________
- Deployed backend URL (for direct `curl`/Postman checks): __________
- Use a fresh test account for this pass — don't reuse a dev-seeded account, so the CRUD scenarios below start from a known-empty state.

## Auth

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 1 | Signup — happy path | `POST /api/auth/register` with valid `email`/`password`/`displayName` | `201`, body has `token` + `expiresAt` | | |
| 2 | Signup — invalid email format | Register with `email: "not-an-email"` | `400`, `fieldErrors` includes `email` | | |
| 3 | Signup — duplicate email | Register twice with the same email | Second attempt `409` | | |
| 4 | Login — happy path | `POST /api/auth/login` with the account from #1 | `200`, `token` + `expiresAt` | | |
| 5 | Login — wrong password | Login with correct email, wrong password | `401` | | |
| 6 | Protected endpoint with valid token | `GET /api/trips` with `Authorization: Bearer <token>` from #4 | `200` | | |
| 7 | Protected endpoint, no token | `GET /api/trips` with no `Authorization` header | `401` | | |
| 8 | Protected endpoint, malformed/expired token | `GET /api/trips` with a garbage or expired token | `401` | | |
| 9 | Session persists across refresh | Log in on the frontend, refresh the page | Still logged in (token persisted client-side, no forced re-login) | | |

## Trip CRUD

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 10 | Create trip | `POST /api/trips` with title + ≥1 stop | `201`, trip object returned | | |
| 11 | View trip | `GET /api/trips/{id}` as owner | `200`, full trip incl. `stops` | | |
| 12 | Edit trip | `PUT /api/trips/{id}` — change title/description/tags/visibility/stops | `200`, updated trip reflects changes | | |
| 13 | Delete trip | `DELETE /api/trips/{id}` as owner | `204`, then `GET` on same id → `404` | | |
| 14 | List trips paginated | `GET /api/trips?page=0&size=20` | `200`, `content` + `page` block | | |

## Stop CRUD

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 15 | Add stop | `POST /api/trips/{tripId}/stops` | `201`, stop appended at next `stopOrder` | | |
| 16 | Reorder stops | Use trip-edit UI drag-reorder (or `PUT /api/trips/{id}` with reordered `stops[]`) | Stop order persists after reload | | |
| 17 | Delete stop | `DELETE /api/trips/{tripId}/stops/{stopId}` | `204`, remaining stops renumbered (no gap in `stopOrder`) | | |

## Authorization boundaries

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 18 | 401 on unauthenticated protected request | Any `/api/trips/**` call with no token | `401` via `JsonAuthenticationEntryPoint` | | |
| 19 | 403 on another user's PRIVATE trip | Create trip as user A (default `PRIVATE`), `GET /api/trips/{id}` as user B | `403` | | |
| 20 | Non-owner cannot edit/delete | `PUT`/`DELETE /api/trips/{id}` as non-owner user B | `403` | | |

## Known-issue log

Record anything that fails here with severity (blocker / major / minor) and a one-line description — feeds into SCRUM-172's consolidated sign-off.

| # | Description | Severity | Workaround / notes |
|---|---|---|---|
| | | | |
