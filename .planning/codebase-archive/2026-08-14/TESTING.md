# Testing Patterns

**Analysis Date:** 2026-08-06

## Test Framework

### Backend

**Test Runner & Frameworks:**
- JUnit 5 (`org.junit.jupiter`) with Surefire (unit tests) and Failsafe (integration tests)
- Mockito for mocking (`@ExtendWith(MockitoExtension.class)`, `@MockitoBean`)
- AssertJ for assertions (`assertThat()`, `assertThatThrownBy()`)
- Spring Test for `@WebMvcTest`, `@SpringBootTest`, `MockMvc`
- Testcontainers (PostgreSQL) for integration tests
- MockRestServiceServer for external HTTP mocking

**Run Commands:**
```bash
mvn verify                    # Unit tests only (*Test.java via Surefire)
mvn verify -Pci               # Full suite: unit + integration tests (*IT.java via Failsafe + Testcontainers)
mvn test -Dtest=ClassName    # Run a single unit test class
```

### Frontend

**Test Runner & Framework:**
- Karma + Jasmine (Google's test runner + BDD-style assertions)
- HttpClientTestingModule for mocking HTTP
- jasmine.SpyObj for spying on service methods
- TypeScript strict mode for type-safe tests

**Run Commands:**
```bash
npm test                      # Watch mode (auto-rerun on file changes)
npm run test:ci               # Headless run with coverage (no-watch, ChromeHeadlessCI)
npm run lint                  # ESLint validation (must pass before PR)
```

## Test File Organization

### Backend

**Location (co-located with source):**
- Source: `backend/src/main/java/com/tripflow/backend/service/TripService.java`
- Test: `backend/src/test/java/com/tripflow/backend/service/TripServiceTest.java`

**Naming:**
- Unit tests: `*Test.java` (discovered and run by Surefire)
- Integration tests: `*IT.java` (discovered and run by Failsafe under `-Pci` profile)
- Fixture/support: `testsupport/` directory (e.g., `PostgresTestcontainersConfiguration.java`)

**Directory Structure:**
```
backend/src/test/java/com/tripflow/backend/
├── service/
│   ├── TripServiceTest.java                (unit)
│   ├── AuthServiceTest.java                (unit)
│   └── ...
├── controller/
│   ├── TripControllerIT.java               (integration)
│   ├── AuthControllerIT.java               (integration)
│   └── AuthControllerTest.java             (slice test: @WebMvcTest)
├── domain/
│   ├── TripPersistenceIT.java              (integration)
│   └── BaseEntityTest.java                 (unit)
├── repository/
│   ├── TripRepositoryIT.java               (integration)
│   └── ...
└── testsupport/
    └── PostgresTestcontainersConfiguration.java
```

### Frontend

**Location (co-located with source):**
- Source: `frontend/src/app/core/services/trip.service.ts`
- Test: `frontend/src/app/core/services/trip.service.spec.ts`

**Naming:**
- All tests: `*.spec.ts` (discovered by Karma)

**Directory Structure:**
```
frontend/src/app/
├── core/
│   ├── services/
│   │   ├── trip.service.ts
│   │   └── trip.service.spec.ts
│   ├── guards/
│   │   ├── auth.guard.ts
│   │   └── auth.guard.spec.ts
│   └── interceptors/
│       ├── auth.interceptor.ts
│       └── auth.interceptor.spec.ts
├── pages/
│   ├── auth/
│   │   ├── login/
│   │   │   ├── login.page.ts
│   │   │   └── login.page.spec.ts
│   │   └── signup/
│   └── trips/
│       ├── dashboard/
│       │   ├── dashboard.page.ts
│       │   └── dashboard.page.spec.ts
│       └── components/
│           ├── stop-list/
│           │   ├── stop-list.component.ts
│           │   └── stop-list.component.spec.ts
```

## Test Structure

### Backend Unit Test

**Pattern (Mockito + AssertJ):**
```java
@ExtendWith(MockitoExtension.class)
public class TripServiceTest {
    @Mock private TripRepository tripRepository;
    @Mock private UserRepository userRepository;
    @Mock private StopService stopService;

    private TripService tripService;

    @BeforeEach
    void setUp() {
        // Real dependencies when needed; mocks for external collaborators
        TripMapper tripMapper = new TripMapper(new StopMapper());
        TripOwnershipService tripOwnershipService = new TripOwnershipService(tripRepository);
        tripService = new TripService(tripRepository, userRepository, tripMapper, tripOwnershipService, stopService);
    }

    @Test
    void createTrip_persistsAndLogsCorrectly() {
        // Arrange
        User owner = new User();
        owner.setId(1L);
        CreateTripRequest request = new CreateTripRequest("Weekend Trip", null, null, TripVisibility.PRIVATE, List.of(...));
        Trip expected = new Trip();
        expected.setId(1L);
        expected.setTitle("Weekend Trip");

        when(userRepository.getReferenceById(1L)).thenReturn(owner);
        when(stopService.buildStops(any(), any())).thenReturn(new ArrayList<>());
        when(tripRepository.save(any())).thenReturn(expected);

        // Act
        TripResponse result = tripService.createTrip(1L, request);

        // Assert
        assertThat(result.title()).isEqualTo("Weekend Trip");
        verify(tripRepository).save(any());
        assertThatThrownBy(() -> tripService.getTrip(999L, 1L))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

**Setup Approach:**
- `@BeforeEach` initializes subject under test with mocks
- Mocks injected via `@Mock` fields (Mockito)
- Real helper objects created when not external dependencies
- When to use real vs. mock: Mock external collaborators (repository, service calls); use real mappers, validators

### Backend Integration Test

**Pattern (Spring Boot + Testcontainers + MockMvc):**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class TripControllerIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private TripRepository tripRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void createTrip_andRetrieveIt_persistsAndReloadsCorrectly() throws Exception {
        // Arrange
        User user = createTestUser("owner1");
        CreateTripRequest tripRequest = new CreateTripRequest("Weekend Trip", null, null, TripVisibility.PRIVATE, List.of(...));

        // Act & Assert
        MvcResult createResult = mockMvc
            .perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tripRequest))
                .with(asUser(user)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Weekend Trip"))
            .andReturn();

        Long tripId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // Retrieve and verify persistence
        mockMvc.perform(get("/api/trips/" + tripId).with(asUser(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Weekend Trip"));
    }

    private User createTestUser(String suffix) {
        User user = new User();
        user.setUsername("integtest-" + suffix);
        user.setEmail("integtest-" + suffix + "@example.com");
        user.setPasswordHash("hashed");
        return userRepository.save(user);
    }

    private RequestPostProcessor asUser(User user) {
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(auth);
    }
}
```

**Key Patterns:**
- `@SpringBootTest` with real application context (full stack)
- `@ImportTestcontainers` wires PostgreSQL container for each test
- `@Transactional` rolls back changes after each test (safe data isolation)
- `@AutoConfigureMockMvc` provides `MockMvc` for HTTP simulation
- Helper methods for test data (`createTestUser()`, `asUser()`, `sampleTripRequest()`)
- `JsonPath` assertions for JSON response validation

### Backend Slice Test (@WebMvcTest)

**Pattern (Controller-only test):**
```java
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private AuthService authService;
    @MockitoBean private JwtService jwtService;  // Required for filter construction

    @Test
    void register_invalidEmail_returns400WithFieldErrors() throws Exception {
        String badEmailJson = "{\"username\":\"tanish\",\"email\":\"not-an-email\",\"password\":\"password123\"}";

        mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(badEmailJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }
}
```

**Purpose:**
- Test HTTP layer (controller + request binding + validation) in isolation
- Mocks service layer; no database involved
- No Testcontainers needed (runs under plain `mvn verify`)
- Fastest feedback for request/response mapping

### Frontend Unit Test

**Pattern (Jasmine + Karma):**
```typescript
describe('TripService', () => {
  let service: TripService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [TripService],
    });
    service = TestBed.inject(TripService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();  // Ensure no outstanding HTTP requests
  });

  it('should list trips and update signal', (done) => {
    const mockSummaries = [
      { id: 1, title: 'Trip 1', visibility: 'PUBLIC' as const, ... },
    ];
    const mockPage = { content: mockSummaries, page: { ... } };

    service.listTrips().subscribe((page) => {
      expect(page).toEqual(mockPage);
      expect(service.trips()).toEqual(mockSummaries);
      done();  // Signal async completion
    });

    const req = httpMock.expectOne('http://localhost:8080/api/trips?page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should handle error without fieldErrors', (done) => {
    service.listTrips().subscribe(
      () => fail('should have failed'),
      (error: any) => {
        expect(error.message).toBe('Internal server error');
        expect(error.fieldErrors).toBeNull();
        done();
      }
    );

    const req = httpMock.expectOne('http://localhost:8080/api/trips?page=0&size=20');
    const mockErrorResponse = { status: 500, message: 'Internal server error', fieldErrors: null };
    req.flush(mockErrorResponse, { status: 500, statusText: 'Internal Server Error' });
  });
});
```

**Setup:**
- `TestBed.configureTestingModule()` with `provideHttpClient()` + `provideHttpClientTesting()`
- `HttpTestingController` to mock HTTP responses
- `httpMock.verify()` in `afterEach()` to catch unexpected requests

### Frontend Component Test

**Pattern (Standalone component):**
```typescript
describe('LoginPage', () => {
  let component: LoginPage;
  let fixture: ComponentFixture<LoginPage>;
  let authService: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(async () => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['login']);

    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginPage);
    component = fixture.componentInstance;
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('navigates to /dashboard on successful login', () => {
    authService.login.and.returnValue(of({
      token: 't', tokenType: 'Bearer', userId: 1, username: 'user', expiresAt: '2026-07-28T00:00:00Z',
    }));
    spyOn(router, 'navigate');

    component.form.setValue({ email: 'user@example.com', password: 'password123' });
    component.onSubmit();

    expect(authService.login).toHaveBeenCalledWith({ email: 'user@example.com', password: 'password123' });
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });
});
```

**Patterns:**
- `jasmine.createSpyObj()` for service mocks with method stubs
- `spyOn()` for router/global methods
- `provideHttpClient()`, `provideRouter()`, `provideIonicAngular()` for component dependencies
- `fixture.detectChanges()` triggers change detection (Angular lifecycle)
- Sync tests unless async RxJS operations are involved

## Mocking

### Backend

**Mockito:**
- `@Mock` fields for dependencies
- `@MockitoBean` for Spring beans in slice/integration tests
- `when(mock.method(...)).thenReturn(value)` for stubbing
- `verify(mock).method(...)` for verifying invocations
- `ArgumentMatchers.any()`, `ArgumentMatchers.eq()` for flexible matching

**External HTTP (MockRestServiceServer):**
- Not extensively used in this codebase (ORS/Gemini clients use RestClient)
- When needed, mock HTTP exchanges in integration tests

**What to Mock:**
- Repository calls (data persistence)
- External service calls (ORS, Gemini)
- Random/time-dependent operations

**What NOT to Mock:**
- Mappers (simple data transformation, real logic)
- Validators (run real validation)
- Security principal construction (tests auth flow end-to-end)

### Frontend

**jasmine.SpyObj:**
- Create spy objects with method stubs:
  ```typescript
  const authServiceSpy = jasmine.createSpyObj('AuthService', ['login', 'logout']);
  ```
- Provide via `TestBed` provider:
  ```typescript
  { provide: AuthService, useValue: authServiceSpy }
  ```
- Verify calls: `expect(authService.login).toHaveBeenCalledWith(...)`

**HttpTestingController:**
- Intercepts all HTTP requests in tests
- `httpMock.expectOne(url)` to assert a single request
- `req.flush(data)` to return mock response
- `httpMock.verify()` in `afterEach()` to detect unexpected requests

**What to Mock:**
- HTTP calls (via HttpClientTestingModule)
- Service methods (via jasmine.SpyObj)
- Router navigation (via spyOn)

**What NOT to Mock:**
- Component DOM (use `fixture.debugElement` to query)
- Angular directives/pipes (use real implementations)

## Fixtures and Factories

### Backend

**Test Fixtures:**
- Helper methods in test class (e.g., `createTestUser()`, `sampleTripRequest()`)
- Found in `*Test.java` and `*IT.java` classes
- Example from `TripControllerIT.java`:
  ```java
  private User createTestUser(String suffix) {
      User user = new User();
      user.setUsername("integtest-" + suffix);
      user.setEmail("integtest-" + suffix + "@example.com");
      user.setPasswordHash("hashed");
      return userRepository.save(user);
  }

  private CreateTripRequest sampleTripRequest(String title, TripVisibility visibility) {
      CreateStopRequest stop = new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null);
      return new CreateTripRequest(title, null, null, visibility, List.of(stop));
  }
  ```

**Testcontainers Configuration:**
- `PostgresTestcontainersConfiguration.java` in `testsupport/` directory
- Provides shared PostgreSQL container for integration tests
- Imported via `@ImportTestcontainers`

### Frontend

**Test Data:**
- Inline mock objects in `describe()` blocks
- Example from `trip.service.spec.ts`:
  ```typescript
  const mockSummaries = [
    { id: 1, title: 'Trip 1', visibility: 'PUBLIC' as const, status: 'DRAFT' as const, ... },
  ];
  const mockPage = { content: mockSummaries, page: { ... } };
  ```
- No separate factory files; data created where needed

## Coverage

### Backend

**Framework:** JaCoCo

**Thresholds (from `docs/ci.md`, baseline per SCRUM-206):**
- Overall: 92% statements
- Changed files: 80% statements
- Measured via CI pipeline on every PR and main push

**View Report:**
```bash
mvn test                    # After running, open target/site/jacoco/index.html
mvn verify -Pci             # Merges unit + integration coverage
```

**How Coverage Works:**
- Unit tests (*Test.java) run via `mvn verify` (Surefire)
- Integration tests (*IT.java) run only under `-Pci` profile (Failsafe + Testcontainers)
- CI merges both `.exec` files; local `mvn verify` shows unit coverage alone by design
- Coverage floors enforced at CI; local runs are informational

### Frontend

**Framework:** Istanbul via `karma-coverage`

**Thresholds (from `karma.conf.js`, measured 2026-07-28):**
- Statements: 93% floor (actual: 96.81%)
- Branches: 84% floor (actual: 88.23%)
- Functions: 90% floor (actual: 93.95%)
- Lines: 94% floor (actual: 97.41%)

**View Report:**
```bash
npm run test:ci              # Runs with coverage; output in coverage/app/
npm test                     # Watch mode; coverage report after each run
# Open: coverage/app/index.html in browser
```

**How It Works:**
- `karma-coverage` instruments source files
- Reports `json-summary` format for CI PR comments
- `ng test --code-coverage` in watch mode auto-updates on file changes
- CI step fails if any metric drops below floor (enforced by karma-coverage, not manual gate)

## Test Types

### Backend

**Unit Tests (*Test.java):**
- Scope: Single class in isolation (service, mapper, utility)
- Dependencies: Mocked via Mockito
- Database: None
- Speed: Fast (~1–5ms per test)
- Run: `mvn verify` via Surefire
- Example: `TripServiceTest`, `StopMapperTest`, `JwtServiceTest`

**Slice Tests (@WebMvcTest, *Test.java):**
- Scope: Controller + request binding + validation (no service/database)
- Dependencies: Service mocked; filter beans constructed but not executed (`addFilters=false`)
- Database: None
- Speed: Fast (~10ms per test)
- Run: `mvn verify` via Surefire
- Example: `AuthControllerTest`, `OrsClientConfigTest`

**Integration Tests (*IT.java):**
- Scope: Full stack (controller → service → database)
- Dependencies: Real Spring context, Testcontainers PostgreSQL, real service logic
- Database: Actual PostgreSQL (via Testcontainers)
- Speed: Slow (~100–500ms per test)
- Run: `mvn verify -Pci` via Failsafe + Testcontainers
- Transactional: `@Transactional` rolls back changes per test
- Example: `TripControllerIT`, `AuthControllerIT`, `TripRepositoryIT`

**Architecture Tests (ArchitectureTest.java):**
- Scope: Layer boundaries and coupling (ArchUnit)
- Dependencies: ArchUnit rules
- Database: None
- Speed: Fast (~100ms)
- Run: `mvn verify` via Surefire
- Example: `ArchitectureTest` — enforces no cross-package imports, layer isolation

### Frontend

**Unit Tests (*.spec.ts):**
- Scope: Service, guard, or interceptor in isolation
- Dependencies: HttpClientTestingModule, jasmine.SpyObj mocks
- DOM: None
- Speed: Fast (~1–5ms per test)
- Example: `trip.service.spec.ts`, `auth.guard.spec.ts`

**Component Tests (*.spec.ts):**
- Scope: Component logic, user interaction, DOM queries
- Dependencies: Real component, mocked services, test fixture
- DOM: QuerySelector, event binding, template queries
- Speed: Medium (~10–50ms per test)
- Example: `login.page.spec.ts`, `dashboard.page.spec.ts`

## Common Patterns

### Backend Async Testing

Not heavily used (synchronous unit tests dominant). When async needed:

```java
@Test
void asyncOperation_completes() throws Exception {
    // Use Thread.sleep() or CompletableFuture.get() sparingly
    CompletableFuture<String> future = someAsyncMethod();
    String result = future.get(5, TimeUnit.SECONDS);
    assertThat(result).isEqualTo("expected");
}
```

### Backend Error Testing

**Domain Exception Testing:**
```java
@Test
void getTrip_nonExistentTrip_throwsResourceNotFoundException() {
    assertThatThrownBy(() -> tripService.getTrip(999L, 1L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Trip not found: 999");
}
```

**HTTP Error Testing (Integration):**
```java
@Test
void deleteTrip_notOwner_returns403() throws Exception {
    // Create trip as user1, try to delete as user2
    Long tripId = createTrip(user1, sampleTripRequest(...));
    
    mockMvc.perform(delete("/api/trips/" + tripId).with(asUser(user2)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("You do not have access to this trip"));
}
```

### Frontend Async Testing

**RxJS Observable Testing:**
```typescript
it('should list trips and update signal', (done) => {
  service.listTrips().subscribe((page) => {
    expect(page).toEqual(mockPage);
    done();  // Signal completion
  });

  const req = httpMock.expectOne('...');
  req.flush(mockPage);
});
```

**Promise-based Async:**
```typescript
it('should delete trip', (done) => {
  component.confirmDelete(trip, new Event('click'));
  
  setTimeout(() => {
    expect(component.trips).not.toContain(trip);
    done();
  }, 100);
});
```

### Frontend Error Testing

**HTTP Error Handling:**
```typescript
it('should handle error with fieldErrors', (done) => {
  service.listTrips().subscribe(
    () => fail('should have failed'),
    (error: any) => {
      expect(error.message).toBe('Validation failed');
      expect(error.fieldErrors).toEqual([{ field: 'title', message: 'Title is required' }]);
      done();
    }
  );

  const req = httpMock.expectOne('...');
  const mockErrorResponse = { status: 400, message: 'Validation failed', fieldErrors: [...] };
  req.flush(mockErrorResponse, { status: 400, statusText: 'Bad Request' });
});
```

---

*Testing analysis: 2026-08-06*
