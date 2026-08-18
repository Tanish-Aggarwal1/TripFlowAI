---
status: complete
phase: 01-auth-seam-hardening
source: [01-VERIFICATION.md]
started: 2026-08-17T23:35:00Z
updated: 2026-08-17T23:59:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Silent refresh + expiry banner/dialog flow (plan 01-04 task 3 human-check)
expected: |
  With the backend running and JWT_EXPIRY_MS temporarily lowered (e.g. 90000):
  1. Log in, leave the tab idle past the access-token expiry. The session renews silently — no visible interruption, no banner, no dialog.
  2. Revoke or otherwise expire the refresh token (e.g. call /api/auth/logout from another tab, or wait out the refresh token). The next silent-refresh attempt fails.
  3. Confirm the inline banner appears above the router outlet without any navigation — the user stays on the current page.
  4. Confirm the first click anywhere in the app after that raises exactly one AlertController dialog (no stacking on rapid clicks), whose action navigates to /login and completes a server-side session revoke.
  5. Log in again and confirm the banner clears (does not loop on the login page).
result: pass
note: |
  Tested on a real login/logout-from-another-tab flow against `auth.guard.ts` in combination with
  the dialog. Confirmed one edge case explicitly: clicking a plain area of the expired page shows
  exactly one dialog with no premature navigation (D-06 "user stays on the current page" holds).
  Clicking a nav/router-link element instead triggers `authGuard`'s own refresh-then-redirect path
  independently of the dialog, silently landing on /login before the dialog is dismissed. Confirmed
  with the user this is expected route-guard behavior (CR-02's fix), not a defect — the dialog's own
  "Log in" button becomes a harmless no-op in that case since the page has already navigated.

## Summary

total: 1
passed: 1
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
