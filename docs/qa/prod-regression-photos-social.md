# Prod Regression — Photos + Community (SCRUM-74c)

Run against the deployed production environment before the Aug 6 presentation. Fill in **Actual** and **Result** as you go.

Tester: __________  Date: __________  Backend build/commit: __________

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
| 1 | Get upload signature | `POST /api/stops/{stopId}/photo-signature` as stop's trip owner | `200`, `cloudName`/`apiKey`/`timestamp`/`signature`/`uploadParams` | | |
| 2 | Upload photo — direct-to-Cloudinary from mobile browser | Use signature from #1 to upload directly to Cloudinary from a mobile browser, then `POST /api/stops/{stopId}/photos` with the resulting `url` | `201`, photo persisted, appears in stop gallery without a backend binary upload | | |
| 3 | Photo appears in gallery + trip detail | After #2, reload trip view | Photo shows in the stop's photo gallery | | |
| 4 | List photos | `GET /api/stops/{stopId}/photos` | `200`, array including the photo from #2 | | |
| 5 | Delete photo | `DELETE /api/stops/{stopId}/photos/{photoId}` | `204`, gone from gallery and from a subsequent `GET` | | |
| 6 | Upload signature — non-owner | Request signature/upload/delete as a user who doesn't own the stop's trip | `403` | | |
| 7 | List photos on a PUBLIC trip's stop, as non-owner | `GET /api/stops/{stopId}/photos` where the parent trip is `PUBLIC`, requester isn't owner | `200` (read allowed on public trips; only owner can add/delete) | | |
| 8 | List photos on a PRIVATE trip's stop, as non-owner | Same but trip is `PRIVATE` | `403` | | |

## Visibility Toggle (real — part of Trip CRUD)

| # | Scenario | Steps | Expected | Actual | Result |
|---|---|---|---|---|---|
| 9 | Toggle PRIVATE → PUBLIC | `PUT /api/trips/{id}` with `visibility: "PUBLIC"` via the edit-trip UI | `200`, trip now readable by non-owners via `GET /api/trips/{id}` | | |
| 10 | Toggle PUBLIC → PRIVATE | Same, back to `PRIVATE` | `200`, non-owner `GET` now `403` | | |

## Not implemented — descope (see scope check above)

| # | Scenario | Status |
|---|---|---|
| 11 | Discovery feed shows only PUBLIC trips | Not implemented — no discovery endpoint |
| 12 | Like a public trip → count increments | Not implemented — no like endpoint |
| 13 | Clone a public trip → deep-copy correctness | Not implemented — no clone endpoint |
| 14 | Search by title, case-insensitive | Not implemented — no search endpoint |

## Known-issue log

| # | Description | Severity | Workaround / notes |
|---|---|---|---|
| | | | |
