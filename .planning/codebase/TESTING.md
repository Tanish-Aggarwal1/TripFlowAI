# Testing Patterns

**Analysis Date:** 2026-08-14

## Test Framework

**Frontend:**
- Runner: Karma + Jasmine
- Config: `frontend/karma.conf.js`
- Assertion library: Jasmine matchers (toBeTrue, toEqual, toBe, etc.)
- Testing utilities: Angular `TestBed` with standalone providers
- Browser: Chrome (headless via ChromeHeadlessCI in CI)

**Backend:**
- Unit tests: JUnit 5 (Jupiter) via Surefire (no Docker, fast local feedback)
- Integration tests: JUnit 5 + Testcontainers (PostgreSQL) via Failsafe under `ci` Maven profile only
- Assertion library: AssertJ (`assertThat()`, `assertThatThrownBy()`)
- Mocking: Mockito for unit tests, `@MockitoBean` for Spring slice tests
- Coverage: JaCoCo merges unit + integration coverage in CI only

**Run Commands:**

Frontend:
```bash
npm test              # Watch mode with browser
npm run test:ci       # Headless, no-watch, with coverage (used in CI)
npm run lint          # ESLint — must pass with zero errors before PR
```

Backend:
```bash
mvn verify            # Unit tests only (Surefire), no Docker required — local dev
mvn verify -Pci       # Full suite: unit + integration tests (Testcontainers) — CI-only
mvn test -Dtest=ClassName    # Single unit test class
```

## Test File Organization

**Frontend:**
- Location: Co-located with source file
- Naming: `*.spec.ts` (e.g., `auth.service.spec.ts`, `app.component.spec.ts`)
- Structure: One spec file per source file
- Example: `frontend/src/app/core/services/auth.service.ts` → `frontend/src/app/core/services/auth.service.spec.ts`

**Backend:**
- Location: Parallel package structure under `src/test/java/`
- Naming: 
  - Unit tests: `*Test.java` (e.g., `AuthServiceTest.java`, `CloudinarySigningServiceTest.java`)
  - Integration tests: `*IT.java` (e.g., `BackendApplicationIT.java`, `AuthControllerIT.java`)
- Structure: One test class per source class; integration tests marked with IT suffix
- Example: `backend/src/main/java/com/tripflow/backend/service/AuthService.java` → `backend/src/test/java/com/tripflow/backend/service/AuthServiceTest.java`

## Test Structure

**Frontend (Jasmine):**

```typescript
describe('AuthService', () => {
  let httpMock: HttpTestingController;

  // Setup: shared constants and factories
  const TOKEN_KEY = 'tripflow_token';
  
  function makeJwt(payload: Record<string, unknown>): string {
    const base64url = (obj: unknown) => btoa(JSON.stringify(obj));
    return `${base64url({ alg: 'none' })}.${base64url(payload)}.sig`;
  }

  // Setup: TestBed configuration
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  // Teardown: verify no outstanding requests
  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  // Grouped test suites
  describe('token persistence', () => {
    it('login success stores token and flips isAuthenticated', (done) => {
      const service = TestBed.inject(AuthService);
      const token = makeJwt({ exp: Math.floor(Date.now() / 1000) + 3600 });
      
      service.login({ email: 'user@example.com', password: 'pass' }).subscribe(() => {
        expect(localStorage.getItem(TOKEN_KEY)).toBe(token);
        done();
      });

      const req = httpMock.expectOne('http://localhost:8080/api/auth/login');
      expect(req.request.method).toBe('POST');
      req.flush({ token, userId: 1, username: 'user', ... });
    });
  });
});
```

**Backend (JUnit 5 + Mockito):**

Unit test example:
```java
class CloudinarySigningServiceTest {

  private static final CloudinaryProperties PROPS = new CloudinaryProperties(
    "demo-cloud", "test-api-key", "secret"
  );

  private final CloudinarySigningService service =
    new CloudinarySigningService(PROPS, FIXED_CLOCK);

  @Test
  void sign_producesExpectedSignatureForCanonicalExample() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("public_id", "sample");

    SignedUploadRequest signed = service.sign(params);

    assertThat(signed.signature()).isEqualTo("c3470533147774275dd37996cc4d0e68fd03cd4f");
  }

  @Test
  void sign_neverIncludesApiSecretInResponse() {
    SignedUploadRequest signed = service.sign(Map.of("public_id", "x"));
    
    assertThat(signed.uploadParams()).doesNotContainKey("api_secret");
    assertThat(signed.toString()).doesNotContain("secret");
  }
}
```

Integration test example (with Testcontainers):
```java
@SpringBootTest
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
class BackendApplicationIT {

  @Test
  void contextLoads() {
    // Verifies Spring context and database connectivity
  }
}
```

Service test example (with Mockito):
```java
class AuthServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @InjectMocks
  private AuthService authService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void register_duplicateEmail_throwsDuplicateEmailException() {
    when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

    assertThatThrownBy(() -> authService.register(new RegisterRequest(...)))
      .isInstanceOf(DuplicateEmailException.class)
      .hasMessageContaining("user@example.com");
  }
}
```

## Mocking

**Frontend (HttpTestingController):**
- Mock HTTP requests via `HttpTestingController`
- `httpMock.expectOne(url)` to assert a single request
- `req.flush(responseBody)` to return success
- `req.error(errorEvent, {status, statusText})` to simulate error
- Always verify no outstanding requests in `afterEach` via `httpMock.verify()`

Pattern from `auth.service.spec.ts`:
```typescript
service.login({ email: 'x@example.com', password: 'wrong' }).subscribe({
  error: (err) => {
    expect(err.message).toBe('Invalid credentials.');
    done();
  },
});

const req = httpMock.expectOne(LOGIN_URL);
req.flush(
  { message: 'Some backend detail' },
  { status: 401, statusText: 'Unauthorized' }
);
```

**Frontend (spyOn):**
- Spy on methods with `spyOn(object, 'method')`
- Verify calls with `toHaveBeenCalledWith(...)`

Example from `auth.service.spec.ts`:
```typescript
const router = TestBed.inject(Router);
spyOn(router, 'navigate');

service.logout();

expect(router.navigate).toHaveBeenCalledWith(['/login']);
```

**Backend (Mockito):**
- `@Mock` for field injection (requires `MockitoAnnotations.openMocks(this)` in `@BeforeEach`)
- `@InjectMocks` for auto-wiring mocks into the class under test
- `@MockitoBean` for Spring test slices (@WebMvcTest)
- `when(mock.method()).thenReturn(value)` for stubbing
- `verify(mock).method()` for verification
- `ArgumentCaptor<T> captor` to capture arguments

Pattern from backend tests:
```java
@Mock
private TripRepository tripRepository;

@InjectMocks
private AiItineraryService aiItineraryService;

@Test
void suggestItinerary_callsGeminiClientWithConstructedPrompt() {
  when(tripRepository.findByIdAndOwnerId(1L, userId))
    .thenReturn(Optional.of(trip));
  when(geminiClient.suggestItinerary(...))
    .thenReturn(response);

  SuggestedItineraryResponse result = 
    aiItineraryService.suggestItinerary(1L, userId, preferences);

  assertThat(result).isNotNull();
  verify(geminiClient).suggestItinerary(...);
}
```

**What to Mock:**
- External HTTP calls (OrsClient, GeminiClient)
- Database repositories in unit tests
- Services with external dependencies
- Router navigation
- Timer/Clock (use fixed `Clock` for determinism)

**What NOT to Mock:**
- Pure utility functions (String operations, parsing)
- Value objects
- DTOs/responses from mocked services (construct actual instances)
- Core domain logic (test the actual service, mock only its dependencies)

## Fixtures and Factories

**Frontend:**
- Test data constructed inline or in helper functions
- Example from `auth.service.spec.ts`:
  ```typescript
  function makeJwt(payload: Record<string, unknown>): string {
    const base64url = (obj: unknown) => btoa(JSON.stringify(obj));
    return `${base64url({ alg: 'none' })}.${base64url(payload)}.sig`;
  }

  const token = makeJwt({ exp: Math.floor(Date.now() / 1000) + 3600 });
  const response = {
    token,
    tokenType: 'Bearer',
    userId: 1,
    username: 'tanish',
    expiresAt: '2026-12-31T23:59:59Z',
  };
  ```
- No fixture files or builder patterns; hardcoded test data preferred for simplicity

**Backend:**
- Test data constructed directly in test methods or setup methods
- Example from `CloudinarySigningServiceTest.java`:
  ```java
  private static final CloudinaryProperties PROPS = new CloudinaryProperties(
    "demo-cloud", "test-api-key", "secret"
  );
  
  private static final Clock FIXED_CLOCK = Clock.fixed(
    Instant.ofEpochSecond(1_315_060_510L), ZoneOffset.UTC
  );

  private final CloudinarySigningService service =
    new CloudinarySigningService(PROPS, FIXED_CLOCK);
  ```
- No separate fixture files or factory builders; direct construction inline
- Static constants for reusable test data

## Coverage

**Frontend:**
- Enforcement: Karma-coverage enforces floor via `check` in `karma.conf.js`
- Floors (measured 2026-07-28):
  - Statements: 93%
  - Branches: 84%
  - Functions: 90%
  - Lines: 94%
- Failure: `npm run test:ci` fails if coverage drops below floors
- Report: Coverage HTML report at `frontend/coverage/app/index.html` after running tests

**Backend:**
- Enforcement: JaCoCo merges unit + integration coverage in CI only
- No local coverage requirement (CI checks after integration tests)
- Floor (backend-ci.yml):
  - Overall: 92%
  - Changed files: 80%
- Failure: CI blocks merge if coverage drops below floor (SCRUM-206)
- No team machine runs Docker locally; `*IT` tests only run in CI under `ci` Maven profile

**View Coverage:**

Frontend:
```bash
npm run test:ci
# Coverage in coverage/app/index.html
```

Backend:
```bash
mvn verify -Pci
# Coverage merges in target/site/jacoco/index.html
```

## Test Types

**Frontend - Unit Tests:**
- Scope: Individual services and components in isolation
- Mocking: HTTP calls via HttpTestingController, external services via spyOn
- Data: Hardcoded test objects
- Speed: <100ms each
- Example: `auth.service.spec.ts` tests token persistence, login/register, error mapping

**Frontend - Component Tests (slice):**
- Scope: Single component with mocked services
- Mocking: Dependencies via TestBed providers
- Template: Rendered via Angular, not directly testable (HTML testing rare)
- Speed: <200ms each
- Not separate from unit tests; components are tested alongside services

**Backend - Unit Tests:**
- Scope: Individual classes (services, utilities, mappers) with mocked dependencies
- Mocking: Repositories, external clients via Mockito
- Data: Hardcoded test objects
- Speed: <50ms each
- Run: `mvn verify` (Surefire)
- Example: `AuthServiceTest`, `CloudinarySigningServiceTest`

**Backend - Integration Tests:**
- Scope: Full Spring context with real database (Testcontainers PostgreSQL)
- Mocking: External APIs only (OrsClient via MockRestServiceServer)
- Data: Flyway migrations run; test data inserted
- Speed: 1-5s each
- Run: `mvn verify -Pci` (Failsafe under `ci` profile only; no local Docker)
- Transactional: Tests run in transactions and rollback after each test
- Example: `AuthControllerIT`, `BackendApplicationIT`

**Backend - Slice Tests:**
- Scope: Single layer (e.g., `@WebMvcTest` for controllers)
- Mocking: Service layer via `@MockitoBean`
- Database: Not loaded; test focused on HTTP handling and validation
- Use: Rarely; preferred approach is full integration tests with Testcontainers in CI

**No E2E Tests:** Selenium/Cypress not used; manual QA or E2E tests run separately outside the repo

## Common Patterns

**Frontend - Async Testing:**

Using `done` callback (Jasmine style):
```typescript
it('login success stores token and flips isAuthenticated', (done) => {
  const service = TestBed.inject(AuthService);
  
  service.login({ email: 'user@example.com', password: 'pass' }).subscribe(() => {
    expect(localStorage.getItem(TOKEN_KEY)).toBe(token);
    done();  // Signal test is complete
  });

  const req = httpMock.expectOne(LOGIN_URL);
  req.flush({ token, userId: 1, username: 'user' });
});
```

Modern async (preferred in new code):
```typescript
it('login success stores token', async () => {
  const service = TestBed.inject(AuthService);
  
  // No manual subscription needed; async/await handles it
  // (not currently used in this codebase, but compatible)
});
```

**Frontend - Error Testing:**

Testing Observable errors:
```typescript
it('network error shows auth-specific message', (done) => {
  const service = TestBed.inject(AuthService);

  service.login({ email: 'x@example.com', password: 'y' }).subscribe({
    error: (err) => {
      expect(err.message).toBe('Network error. Please check your connection and try again.');
      done();
    },
  });

  const req = httpMock.expectOne(LOGIN_URL);
  req.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
});
```

**Backend - Exception Testing:**

Testing exceptions with AssertJ:
```java
@Test
void register_oversizedUsername_throwsValidationException() {
  String tooLong = "x".repeat(256);
  
  assertThatThrownBy(() -> 
    authService.register(new RegisterRequest(tooLong, "email@example.com", "pass"))
  )
    .isInstanceOf(ValidationException.class)
    .hasMessageContaining("username");
}
```

**Backend - Race Condition Testing:**

Example from `AuthService.register()`: pre-checks for duplicates, then catches race condition:
```java
if (userRepository.existsByEmail(request.email())) {
  throw new DuplicateEmailException(request.email());
}

try {
  userRepository.save(user);
} catch (DataIntegrityViolationException ex) {
  String detail = ex.getMostSpecificCause().getMessage();
  if (detail != null && detail.contains(EMAIL_UNIQUE_CONSTRAINT)) {
    throw new DuplicateEmailException(request.email());
  }
  // ... re-throw if constraint not recognized
}
```

No explicit test for the race window (race is inherently hard to test); the pre-check ensures the fast path; the catch ensures correctness in the race scenario.

**Backend - Time-Dependent Tests:**

Use fixed Clock for determinism:
```java
private static final Clock FIXED_CLOCK = Clock.fixed(
  Instant.ofEpochSecond(1_315_060_510L), ZoneOffset.UTC
);

private final CloudinarySigningService service =
  new CloudinarySigningService(PROPS, FIXED_CLOCK);

@Test
void sign_producesExpectedSignature() {
  SignedUploadRequest signed = service.sign(params);
  
  assertThat(signed.timestamp()).isEqualTo(1_315_060_510L);  // Fixed value, repeatable
}
```

**Backend - Test Organization:**

Group related tests in nested describe-like structures (no nesting framework; conventions):
```java
class AuthServiceTest {

  @Nested
  class RegisterTests {
    @Test
    void duplicateEmail_throwsException() { ... }
    
    @Test
    void validRequest_savesUser() { ... }
  }

  @Nested
  class LoginTests {
    @Test
    void invalidPassword_throwsException() { ... }
  }
}
```

Note: `@Nested` class grouping is not used in this codebase; tests are flat with descriptive names.

---

*Testing analysis: 2026-08-14*
