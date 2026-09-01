---
schema_version: 1
open_count: 4
waived_count: 0
fixed_count: 0
total_count: 4
last_updated: 2026-08-31T20:05:39.291Z
---

# Broken Windows Ledger

> Cross-phase defect register. With `workflow.windows_enforce` enabled, `/gsd-ship` blocks while `open_count > 0`.
> Waive with `gsd-tools windows waive <id> "<reason>"` (reason required).
> Mark fixed with `gsd-tools windows fixed <id>`.

| id | phase | kind | file | line | description | status | reason | recorded_at | resolved_at |
|----|-------|------|------|------|-------------|--------|--------|-------------|-------------|
| 1 | 01 | unrun-verify | backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java |  | Six refresh-flow IT scenarios never executed locally - Testcontainers requires Docker, CI-only | open |  | 2026-08-14T21:40:20.821Z |  |
| 2 | 01 | stub | backend/src/main/java/com/tripflow/backend/controller/AuthController.java |  | POST /api/auth/refresh has no rate limit - deferred to plan 01-03 alongside reuse-detection mass-revoke (T-01-10) | open |  | 2026-08-14T21:40:28.013Z |  |
| 3 | 01 | stub | backend/src/main/java/com/tripflow/backend/service/RefreshTokenService.java |  | Replayed already-used refresh token is rejected as generically invalid instead of triggering the D-03 family mass-revoke - fail-closed placeholder for plan 01-03 | open |  | 2026-08-14T21:40:35.780Z |  |
| 4 | 06 | unrun-verify | frontend/src/app/pages/feed/components/feed-card/feed-card.component.html |  | nested=true diagonal-drag gesture disambiguation (D-01) not verified on a real touch device or DevTools touch emulation; Task 3's <human-check> still owed | open |  | 2026-08-31T20:05:39.291Z |  |

````json
[
  {
    "id": 1,
    "kind": "unrun-verify",
    "phase": "01",
    "file": "backend/src/test/java/com/tripflow/backend/controller/AuthControllerIT.java",
    "line": null,
    "description": "Six refresh-flow IT scenarios never executed locally - Testcontainers requires Docker, CI-only",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-14T21:40:20.821Z",
    "resolved_at": null
  },
  {
    "id": 2,
    "kind": "stub",
    "phase": "01",
    "file": "backend/src/main/java/com/tripflow/backend/controller/AuthController.java",
    "line": null,
    "description": "POST /api/auth/refresh has no rate limit - deferred to plan 01-03 alongside reuse-detection mass-revoke (T-01-10)",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-14T21:40:28.013Z",
    "resolved_at": null
  },
  {
    "id": 3,
    "kind": "stub",
    "phase": "01",
    "file": "backend/src/main/java/com/tripflow/backend/service/RefreshTokenService.java",
    "line": null,
    "description": "Replayed already-used refresh token is rejected as generically invalid instead of triggering the D-03 family mass-revoke - fail-closed placeholder for plan 01-03",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-14T21:40:35.780Z",
    "resolved_at": null
  },
  {
    "id": 4,
    "kind": "unrun-verify",
    "phase": "06",
    "file": "frontend/src/app/pages/feed/components/feed-card/feed-card.component.html",
    "line": null,
    "description": "nested=true diagonal-drag gesture disambiguation (D-01) not verified on a real touch device or DevTools touch emulation; Task 3's <human-check> still owed",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-31T20:05:39.291Z",
    "resolved_at": null
  }
]
````
