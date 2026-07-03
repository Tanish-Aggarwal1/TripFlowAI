# Authentication

## Overview
[1-2 sentences: JWT-based stateless auth via Spring Security]

## Token Flow
1. User registers/logs in via /api/auth/register or /api/auth/login
2. Server validates credentials, issues a JWT signed with [algorithm]
3. Client stores token and sends it as `Authorization: Bearer <token>` on every request
4. JwtAuthFilter intercepts each request, validates the token, sets the SecurityContext
5. Token expires after [X] — configured via JWT_EXPIRY_MS in .env

## Key Classes
- `SecurityConfig` — filter chain, permitted paths, PasswordEncoder bean
- `JwtService` — generate/parse/validate tokens
- `JwtAuthFilter` — per-request token extraction and SecurityContext setup
- `AuthService` / `AuthController` — register/login business logic

## How to Add a New Protected Endpoint
[Fill in once Story 2 is done — e.g. "Nothing extra needed, SecurityConfig denies all by default except /api/auth/**"]

## Environment Variables Required
- `JWT_SECRET` — signing key, set in backend/.env, never committed
- `JWT_EXPIRY_MS` — token lifetime in milliseconds

## Testing
See auth unit tests in `AuthServiceTest.java`. Manual verification via Postman collection: `docs/postman/tripflow-auth.postman_collection.json`