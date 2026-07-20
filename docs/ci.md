# CI/CD Pipeline

## Overview
GitHub Actions runs on every PR targeting main. A failing pipeline blocks merge (required status check).

## Workflow File
`.github/workflows/backend-ci.yml`

## Triggers
- Pull request opened/updated targeting `main`
- Push to `main` (added SCRUM-135) — cancels any in-flight run for the same ref via a `concurrency` group, so a rapid sequence of merges doesn't queue up  redundant runs
- `frontend-ci.yml` triggers separately, path-scoped to `frontend/**` only (SCRUM-137)

## Stages
1. Checkout code
2. Set up JDK 21
3. Cache Maven dependencies
4. Run `mvn -B verify -Pci` (unit tests + Testcontainers integration tests)
5. Merge JaCoCo unit + integration coverage data and generate the HTML/XML report
6. Upload JaCoCo HTML report as a build artifact
7. Post JaCoCo coverage summary as a PR comment

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
| `mvn verify` | Fast feedback — unit tests only, no Docker |
| `mvn verify -Pci` | Full suite — unit + `*IT` integration tests via Testcontainers |

## What Blocks a Merge
- Any failing test
- Build/compile failure
- Coverage below the floor set in `min-coverage-overall`/`min-coverage-changed-files` (SCRUM-136) — currently 80% / 80%, set 2026-07-19 based on the ~81.53% overall figure reported before this session's SCRUM-100/102/135-137 work landed, rounded down for a safe buffer. Not yet re-verified against a CI run including the new files from this session (`UserPrincipal`, `JsonAuthenticationEntryPoint`, `JsonAccessDeniedHandler`, `SecurityErrorWriter`) — revisit once a real number is available.

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