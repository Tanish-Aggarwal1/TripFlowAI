# Security Policy

TripFlowAI is a student capstone project (Sheridan College). It is not a production commercial service, but it does handle real user credentials (hashed passwords, JWTs) and integrates with third-party APIs using real API keys, so we treat security seriously within that scope.

## Reporting a Vulnerability

If you find a security issue in this repository (e.g. an auth bypass, an injection vulnerability, exposed secrets in git history), please do **not** open a public GitHub issue. Instead, contact the team directly:

- Tanish Aggarwal (repo admin) — see GitHub profile for contact
- Or open a private security advisory via GitHub's "Report a vulnerability" feature on this repository's Security tab

We'll acknowledge reports within a reasonable timeframe given this is a part-time student project, and will credit reporters in the fix's commit/PR unless anonymity is requested.

## Scope

In scope: this repository's backend and frontend code, its GitHub Actions workflows, and its documented deployment configuration.

Out of scope: the third-party services we integrate with (OpenRouteService, Google Gemini, Mapbox, Cloudinary, Render/Railway) — report issues with those directly to their own security teams.

## Known Security Practices

- Passwords are hashed with BCrypt, never stored or logged in plaintext.
- JWTs are never logged (see `docs/LOGGING_STANDARD.md`).
- Secrets (`JWT_SECRET`, database credentials, API keys) are never committed — managed via `.env` (gitignored) locally and platform environment variables in production.
- `ddl-auto=validate` in every environment — schema changes only via reviewed Flyway migrations, never silent auto-DDL.
- See `docs/risk-register.md` for tracked security-adjacent risks (e.g. R2: JWT filter misconfiguration risk).