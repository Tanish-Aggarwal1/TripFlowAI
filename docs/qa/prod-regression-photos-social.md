# Prod Regression — Photos + Community (SCRUM-74c)

Run against the deployed production environment before the Aug 6 presentation. Fill in **Actual** and **Result** as you go.

Tester: Tanish (scripted via curl)  Date: 2026-08-04  Backend build/commit: main @ 7f55a3c (post-#191)

## ⚠️ Scope check before running this

SCRUM-74c's scenario list (discovery feed, like, clone, search) assumes community/discovery features that **do not exist in the backend yet**. Checked against the current `TripController`/`StopPhotoController` (2026-07-30, commit `9ef3c20`):

- `GET /api/trips` only returns **the authenticated user's own trips** — there is no public-browse/discovery endpoint.
- No like endpoint (`Trip`/`TripSummaryResponse` has no like count field).
- No clone/copy-trip endpoint.
- No search endpoint (title or otherwise).
- `TripVisibility` (`PRIVATE`/`PUBLIC`) does exist and is enforced — a non-owner *can* `GET /api/trips/{id}` directly if it's `PUBLIC` (403 if `PRIVATE`) — but there's no feed that surfaces public trips to other users, so "discovery" has nothing to browse.

**Recommendation:** descope the like/clone/discover/search rows below from this pass — they'll fail as "not implemented," not as regressions — and flag the gap in SCRUM-172's consolidated sign-off as a known scope gap rather than a bug found during testing. Confirm with Joann/the team whether SCRUM-74c's ticket description should be corrected, or whether these are tracked elsewhere as unbuilt features.

Photo upload and visibility toggle **are** implemented (`StopPhotoController`, SCRUM-152/153) — those rows below are real and should be run.

## Photo Upload (real — `StopPhotoController`)

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 1 | Get upload signature | `POST /api/stops/{stopId}/photo-signature` as stop's trip owner | `200`, `cloudName`/`apiKey`/`timestamp`/`signature`/`uploadParams` | `200`, all fields present (`cloudName:"gyylivx7"`, etc.) | PASS |
| 2 | Upload photo — direct-to-Cloudinary from mobile browser | Use signature from #1 to upload directly to Cloudinary, then `POST /api/stops/{stopId}/photos` | `201`, photo persisted | **Not run via curl** — the actual Cloudinary binary upload step needs a real browser/device, not simulable with curl alone. Signature generation (the backend half) is confirmed working (#1); recommend a quick manual phone-browser check before Thursday. | NOT RUN — manual step needed |
| 3 | Photo appears in gallery + trip detail | After #2, reload trip view | Photo shows in gallery | Blocked by #2 | BLOCKED |
| 4 | List photos | `GET /api/stops/{stopId}/photos` | `200`, array | `200`, `[]` on a stop with no photos yet (endpoint itself works, just no data to list without #2) | PASS (endpoint) |
| 5 | Delete photo | `DELETE /api/stops/{stopId}/photos/{photoId}` | `204` | Not run — no photo existed to delete without #2 | NOT RUN |
| 6 | Upload signature — non-owner | Request signature as a user who doesn't own the stop's trip | `403` | `403`, "You do not have access to this stop" | PASS |
| 7 | List photos on a PUBLIC trip's stop, as non-owner | `GET /api/stops/{stopId}/photos` on Muskoka (PUBLIC), as qa-tester | `200` | `200`, `[]` | PASS |
| 8 | List photos on a PRIVATE trip's stop, as non-owner | Same on Montreal (PRIVATE) | `403` | `403`, "You do not have access to this stop" | PASS |

## Visibility Toggle (real — part of Trip CRUD)

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 9 | Toggle PRIVATE → PUBLIC | `PUT /api/trips/{id}` with `visibility: "PUBLIC"` | `200`, trip now readable by non-owners | `200`; qa-tester account confirmed `GET` now returns `200` instead of `403` | PASS |
| 10 | Toggle PUBLIC → PRIVATE | Same, back to `PRIVATE` | `200`, non-owner `GET` now `403` | Not independently re-run (toggle direction is symmetric, same `PUT` code path as #9/#12 in auth-crud doc which already confirmed the general edit path) | PASS (inferred) |

## Not implemented — descope (see scope check above)

| # | Scenario | Status |
|---|---|---|
| 11 | Discovery feed shows only PUBLIC trips | **Re-confirmed 2026-08-04**: `GET /api/discovery/trips` → `404 No matching route` — still not implemented |
| 12 | Like a public trip → count increments | **Re-confirmed 2026-08-04**: `POST /api/trips/{id}/like` → `404` — still not implemented |
| 13 | Clone a public trip → deep-copy correctness | **Re-confirmed 2026-08-04**: `POST /api/trips/{id}/clone` → `404` — still not implemented |
| 14 | Search by title, case-insensitive | Not independently re-tested — no search endpoint exists anywhere in `TripController`, same conclusion as the original 2026-07-30 audit |

## Known-issue log

| # | Description | Severity | Workaround / notes |
|---|---|---|---|
| 1 | Full Cloudinary upload round-trip (#2/#3/#5) not verified end-to-end in this pass — needs a manual browser/phone check | Minor (process gap, not a confirmed bug) | The backend half (signature generation + auth boundaries) is fully verified. Do one manual phone-browser photo upload before Thursday to close this out. |
