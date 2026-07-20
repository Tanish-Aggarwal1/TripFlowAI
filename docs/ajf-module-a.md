# AJF Module A — Intelligent Trip Engine
**Owners:** Tanish, Neel

## Sprint 1
- Implemented Spring Security + JWT authentication (stateless, BCrypt hashing, custom filter chain)
- Built signup/login UI consuming the auth API

## Sprint 3
- Custom `AuthenticationEntryPoint` + `AccessDeniedHandler` (SCRUM-100/REF-11) — replaces Spring Security's default HTML error pages with canonical JSON, demonstrating the strategy-pattern extension points Spring Security exposes for exactly this purpose
- Typed `UserPrincipal implements UserDetails` (SCRUM-102/REF-13) — replaces a raw `Long` principal + manual string parsing with a proper `UserDetails` implementation resolved via `@AuthenticationPrincipal`, demonstrating idiomatic Spring Security identity handling across the full filter → controller pipeline


## Presentation Notes
[Running list of what's demo-worthy — update each sprint, don't wait till the end]