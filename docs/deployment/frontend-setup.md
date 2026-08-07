# Frontend Deployment (Render Static Site)

Covers the deployed frontend PWA — hosting, build configuration, and verification status. Backend deployment (env vars, deploy process, rollback) is documented separately in `docs/deployment.md`, which is authoritative for the backend.

## Deployed URL

`https://tripflowai-frontend.onrender.com`

Deployed against the live backend at `https://tripflowai.onrender.com` (see `docs/deployment.md`). Render free tier spins the backend down when idle — hit `/actuator/health` a few minutes before a demo to warm it up (same cold-start note as `docs/SDP/SDP.md` §10).

## Hosting

Render Static Site, auto-deploying from `main` on push (same platform/pattern as the backend, configured directly in the Render dashboard rather than checked into the repo).

- Build command: `npm run build` (Angular production build, `frontend/`)
- Publish directory: `frontend/www` (Ionic's default `ng build` output)
- Build-time environment variables (Mapbox token, API base URL) are set directly in Render's dashboard for this service — **not** the same secrets used by `.github/workflows/frontend-ci.yml`, which only builds for CI verification and never ships its artifact. See `docs/frontend-standards.md` §Environment Files for how the two paths differ.

## Verification status

| Check | Status |
|---|---|
| Deployed URL loads without errors | Verified — `docs/qa/prod-regression-auth-crud.md` |
| Signup/login work against the deployed backend | Verified — `docs/qa/prod-regression-auth-crud.md` |
| No CORS errors in browser console | Verified during the same regression pass (login flow completed against the live backend, which requires a clean CORS preflight) |
| PWA install prompt (mobile Safari + Chrome) | Not yet verified — open item |
| Lighthouse score (PWA + Performance + Accessibility) ≥ 90 | Not yet captured — open item |

The two open items above are tracked as follow-up work, not blockers for the current deployment — the app is live and functionally verified, but a formal Lighthouse pass and install-prompt check on physical devices haven't been run yet.

## Redeploying

Push to `main` (frontend changes only trigger `frontend-ci.yml` for CI, per its `paths: frontend/**` filter). Render's own auto-deploy watches `main` independently and rebuilds on every push, using the build-time env vars configured in its dashboard.
