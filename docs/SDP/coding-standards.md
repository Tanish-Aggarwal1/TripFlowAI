# Coding Standards

Companion to `docs/SDP/SDP.md` §8. This file is the detailed reference; the SDP section summarizes.

## Java / Backend

### Dependency Injection
- Constructor injection via Lombok `@RequiredArgsConstructor` with `private final` fields.
- `@AllArgsConstructor` is not used in new production code (a few legacy classes remain; migration tracked separately).
- Never use field injection (`@Autowired` on a field) — it hides dependencies and breaks testability.

### DTOs
- All request/response types are Java **records**, not classes.
- Records live in `com.tripflow.backend.dto`.
- External-API wire-format DTOs (Gemini, ORS) live in `client/{service}/` alongside the client, separate from domain DTOs.

### Entities
- Use Lombok `@Getter @Setter @NoArgsConstructor` on JPA entities.
- Extend `BaseEntity` for `id`, `createdAt`, `updatedAt` auditing — never redeclare these fields.
- Timestamps are `Instant` (mapped to PostgreSQL `TIMESTAMPTZ`), never `LocalDateTime`.

### Naming
- Classes: `PascalCase`. One public class per file.
- Methods/variables: `camelCase`.
- Constants: `UPPER_SNAKE_CASE`.
- Packages: all lowercase, no underscores.
- Test classes: `*Test.java` (Surefire/unit), `*IT.java` (Failsafe/integration).

### Nullability
- Return `Optional<T>` from repository finders (Spring Data default).
- Never return `null` from a service method — throw a domain exception instead (`ResourceNotFoundException`, etc.).
- Use `@Valid` on controller request bodies to fail fast on missing required fields.

### Logging
- See `docs/LOGGING_STANDARD.md` for the full standard.
- SLF4J via `@Slf4j`, never `System.out` or `printStackTrace()`.
- Parameterized messages (`log.info("msg key={}", val)`), never string concatenation.

### Exception Handling
- Services throw domain exceptions; `GlobalExceptionHandler` translates to HTTP status + `ApiError`.
- Never catch-and-swallow silently. If you must swallow, log at `WARN` with a rationale comment.
- External-API failures translate to dedicated exceptions (`OrsClientException`, `GeminiClientException`) → 502.

### External Client Pattern (`client/{service}/`)
Every external integration follows the same structure:
- `*Properties` — `@ConfigurationProperties` record with `@Validated`
- `*ClientConfig` — `@Configuration` class registering the properties and the `RestClient` bean
- `*Client` — thin wrapper around `RestClient`, translating failures to typed exceptions
- Wire-format DTOs — separate records matching the external API's JSON, annotated with `@JsonIgnoreProperties(ignoreUnknown = true)` on responses

### Formatting
- Indentation: tabs (Eclipse default, configured in project settings).
- Max line length: soft limit 120 characters.
- Imports: no wildcard imports (`*`), organize imports alphabetically.

## Frontend (Ionic / Angular)

See `docs/frontend-standards.md` for the full frontend standard.