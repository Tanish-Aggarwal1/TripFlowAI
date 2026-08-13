# CI/CD Pipeline

## Overview
GitHub Actions runs on every PR targeting main. A failing pipeline blocks merge (required status check).

## Workflow File
`.github/workflows/backend-ci.yml`

## Triggers
- Pull request opened/updated targeting `main`
- Push to `main` (added SCRUM-135)
- `frontend-ci.yml` triggers separately, path-scoped to `frontend/**` only (SCRUM-137)

The `concurrency` group is keyed on `github.workflow`-`github.ref`, but `cancel-in-progress` (SCRUM-467) only applies on `pull_request` events — a rapid sequence of pushes to the same PR branch cancels the stale run, but two quick merges to `main` do not cancel each other, so every merged commit keeps a recorded CI verdict for the post-merge audit trail (Render auto-deploys from `main` independently of this check).

## Stages
1. Checkout code
2. Set up JDK 21
3. Cache Maven dependencies
4. Run `.\mvnw.cmd -B verify -Pci` (unit tests + Testcontainers integration tests)
5. Merge JaCoCo unit + integration coverage data and generate the HTML/XML report
6. Upload JaCoCo HTML report as a build artifact
7. Post JaCoCo coverage summary as a PR comment

Each job sets `timeout-minutes: 20` (backend) / `10` (frontend). Without it, a Testcontainers pull that never becomes healthy — or any other hang — inherits GitHub's 6-hour default and leaves the required check pending forever, blocking every PR from merging with no visible failure to point at.

`frontend-ci.yml` also gates on a "Verify Mapbox secret is set" step before injecting `secrets.MAPBOX_TOKEN` — it fails fast with an explicit error if the repo secret is missing, rather than letting the build silently ship a placeholder token.

## Coverage Measurement

JaCoCo instruments two separate test runs:
- **Surefire** (`*Test.java`, unit tests) → writes `target/jacoco.exec`
- **Failsafe** (`*IT.java`, integration tests, CI-only via `-Pci`) → writes `target/jacoco-it.exec`

A `merge` execution combines both `.exec` files into `target/jacoco-merged.exec` before the `report` execution reads it. Both executions are bound to the Maven `verify` phase, declared in that order inside the same `jacoco-maven-plugin` block in `backend/pom.xml`.

**Locally** (`mvn verify`, no `-Pci`, no Docker): only `jacoco.exec` exists — Failsafe never runs.
The report reflects unit-test coverage only. This is expected, not a bug.

**In CI** (`mvn verify -Pci`): both `.exec` files exist and get merged, so the report reflects true combined coverage — including everything only exercised by `*IT` tests (e.g. controller endpoints hit exclusively through `TripIntegrationIT`, `AuthControllerIntegrationIT`, etc.).

Before this fix, `report` only ever read `jacoco.exec`, so any line covered exclusively by an `*IT` test reported as 0% regardless of actual test coverage.

## Local commands

| Command | When to use |
|---------|-------------|
| `.\mvnw.cmd verify` (PowerShell) / `./mvnw verify` (bash) | Fast feedback — unit tests only, no Docker |
| `.\mvnw.cmd verify -Pci` (PowerShell) / `./mvnw verify -Pci` (bash) | Full suite — unit + `*IT` integration tests via Testcontainers |

## What Blocks a Merge
- Any failing test
- Build/compile failure
- Coverage below the floor set in `min-coverage-overall`/`min-coverage-changed-files` (SCRUM-136, re-verified SCRUM-206) — **92% / 80%, measured 2026-07-26**. `min-coverage-overall` had been a placeholder 80/80 since SCRUM-136 (comment literally said "TEMP placeholder... no real coverage number to lock yet"). Pulled the actual overall coverage from the JaCoCo PR comment on several consecutive green `backend-ci` runs (PRs #118, #120, #121): consistently **96%** overall. Set `min-coverage-overall` to 92 (a few points below the measured number, never above, per SCRUM-136's original guidance) and kept `min-coverage-changed-files` at 80 (within SCRUM-136's suggested 70-80 range). Re-measure and adjust if the suite's overall coverage shifts meaningfully.

## Frontend Coverage Gate (SCRUM-214/236/247)

Before SCRUM-214, `frontend-ci.yml` measured Karma/Istanbul coverage and posted it as a PR comment, but nothing enforced a floor — coverage could fall to zero and CI would still pass. The entire client-side auth path (`auth.service.ts`, `auth.guard.ts`, `auth.interceptor.ts`, `session-expiry.interceptor.ts`) had zero tests until this ticket.

`karma.conf.js`'s `coverageReporter.check.global` now sets a floor per metric. `karma-coverage` fails the `npm run test:ci` step itself (non-zero exit) when a metric drops below its floor — `frontend-ci.yml` needs no separate gating step, unlike the backend's JaCoCo-based approach.

**Measured 2026-07-28** (SCRUM-247), after rewriting `trip-map.component.spec.ts` to mock the `mapboxgl.Map`/`Marker`/`Popup`/`LngLatBounds` constructors (the previous spec let `initMap` construct a real, WebGL-less map that failed and short-circuited before most of the component ever ran), and adding real specs to `login.page`, `signup.page`, and the untested write methods of `trip.service` (150 tests, up from 98):

| Metric | Measured | Floor |
| --- | --- | --- |
| Statements | 96.81% | 93% |
| Branches | 88.23% | 84% |
| Functions | 93.95% | 90% |
| Lines | 97.41% | 94% |

Same method SCRUM-206 used for the backend: measure the real number first, then set the floor a few points below it — never pick a number before measuring. Re-measure and adjust if the suite's coverage shifts meaningfully (e.g. once SCRUM-71/72 add new pages).

Verified the gate actually fails the build: temporarily set the `statements` floor to 99% locally, ran `npm run test:ci`, confirmed it failed with `Coverage for statements (96.81%) does not meet global threshold (99%)` and a non-zero exit code, then reverted to the real floor.

## How to Read a Failure
1. Open the failed check on the PR
2. Click "Details" to view the Actions log
3. Look for the first red ✗ step — that's where it failed
4. Common causes hit this sprint:
   - Mockito inline-mock-maker self-attach warning (JDK deprecation notice, not a failure — tracked separately as SCRUM-198)
   - `InvalidDefinitionException` on `java.time.*` types — a plain `ObjectMapper` (in production code or in a test's own local instance) missing `JavaTimeModule`
   - Testcontainers/Docker-dependent `*IT` tests can only be diagnosed via the CI log, never locally — no team machine has Docker

## Required Status Check
Enabled in Settings → Branches → main → branch protection. PR cannot merge until this check is green.

## Screenshot Evidence
[Attach: green pipeline run, and one red pipeline run showing a blocked merge]