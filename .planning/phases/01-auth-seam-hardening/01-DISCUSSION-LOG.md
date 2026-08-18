# Phase 1: Auth Seam Hardening - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-11
**Phase:** 1-Auth Seam Hardening
**Areas discussed:** Refresh token delivery, Rotation & reuse policy, Access-token lifetime, Frontend refresh trigger, Logout scope, Refresh failure handling

---

## Refresh token delivery

| Option | Description | Selected |
|--------|-------------|----------|
| httpOnly cookie | Set-Cookie on login/refresh; JS can't read it, mitigates XSS theft | ✓ |
| JSON response body | Client stores in memory/localStorage; exposed to XSS | |
| You decide | Claude picks based on codebase conventions | |

**User's choice:** httpOnly cookie
**Notes:** None given beyond selection.

---

## Rotation & reuse policy

| Option | Description | Selected |
|--------|-------------|----------|
| Revoke all sessions | Reuse of a rotated token = compromise signal; revoke every refresh token for the user | ✓ |
| Revoke only that token | Narrower blast radius, leaves other possibly-stolen tokens valid | |

**User's choice:** Revoke all sessions
**Notes:** None given beyond selection.

---

## Access-token lifetime

| Option | Description | Selected |
|--------|-------------|----------|
| 15 minutes | Short-lived, limits exposure, silent refresh covers UX | ✓ |
| Keep current value | Whatever JWT_EXPIRY_MS already is | |
| You decide | Claude picks a reasonable value | |

**User's choice:** 15 minutes
**Notes:** None given beyond selection.

---

## Frontend refresh trigger

| Option | Description | Selected |
|--------|-------------|----------|
| Reactive — 401 interceptor | Catch 401, call refresh, retry request | |
| Proactive — timer before expiry | Schedule refresh shortly before TTL expires | ✓ |

**User's choice:** Proactive — timer before expiry
**Notes:** None given beyond selection.

---

## Logout scope

| Option | Description | Selected |
|--------|-------------|----------|
| Current device only | Logout doesn't affect other sessions | ✓ |
| All devices | Logout anywhere ends every session everywhere | |

**User's choice:** Current device only
**Notes:** None given beyond selection.

---

## Refresh failure handling

| Option | Description | Selected |
|--------|-------------|----------|
| Redirect to login | Clear auth state, navigate to login immediately | |
| Show inline banner | Stay on page, show "session expired" banner | ✓ (modified) |

**User's choice:** Show inline banner, but if the user then clicks/interacts with anything after expiry, intercept and show a "your session expired" dialog that leads to login.
**Notes:** Two-stage behavior — passive banner on failure, then a hard interception gate on the next user interaction. Captured as D-06 in CONTEXT.md.

---

## Claude's Discretion

- Cookie attributes (Path, Domain, Secure flag per environment)
- Whether CSRF token protection is additionally needed alongside SameSite

## Deferred Ideas

None — discussion stayed within phase scope.
