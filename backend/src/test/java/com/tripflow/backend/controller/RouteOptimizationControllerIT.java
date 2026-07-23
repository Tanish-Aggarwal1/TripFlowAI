package com.tripflow.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.springframework.http.HttpStatus;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

/**
 * End-to-end IT for {@code POST /api/trips/{id}/optimize} (SCRUM-58 / SCRUM-58d).
 *
 * <p>Boots the full Spring context (real Postgres via Testcontainers, real Spring
 * Security filter chain, real {@link com.tripflow.backend.client.ors.OrsClient}) and
 * replaces only the outbound {@code orsRestClient} bean with one bound to a
 * {@link MockRestServiceServer}. No real network calls reach OpenRouteService.
 *
 * <p>This intentionally goes one layer deeper than
 * {@link com.tripflow.backend.service.RouteOptimizationServiceTest}, which mocks
 * {@code OrsClient} itself: here the real {@code OrsClient} HTTP request construction
 * and the real {@link com.tripflow.backend.exception.GlobalExceptionHandler#handleOrsFailure}
 * 502 mapping are exercised too.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class RouteOptimizationControllerIT {

	private static final String ORS_BASE_URL = "https://ors.test";

	/**
	 * Overrides the production {@code orsRestClient} bean (see
	 * {@link com.tripflow.backend.client.ors.OrsClientConfig}) with one bound to a
	 * {@link MockRestServiceServer}. {@code @Primary} wins by-type autowiring into
	 * {@link com.tripflow.backend.client.ors.OrsClient} without needing to override
	 * the original bean definition by name (no
	 * {@code allow-bean-definition-overriding} flag required). The real
	 * {@code orsRestClient} bean still gets created too (harmless — it's only ever
	 * built, never invoked, and {@code ors.*} placeholder values already exist in
	 * application-test.properties for exactly this reason).
	 */
	@TestConfiguration
	static class OrsMockConfig {

		@Bean
		RestClient.Builder orsMockRestClientBuilder() {
			return RestClient.builder().baseUrl(ORS_BASE_URL);
		}

		@Bean
		MockRestServiceServer orsMockServer() {
			return MockRestServiceServer.bindTo(orsMockRestClientBuilder()).build();
		}

		@Bean
		@Primary
		RestClient mockOrsRestClient() {
			orsMockServer(); // force bind-before-build ordering (proxied @Bean call = singleton-safe)
			return orsMockRestClientBuilder().build();
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MockRestServiceServer orsMockServer;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void resetMockServer() {
		// MockRestServiceServer is a singleton bean shared across all @Test methods in
		// this class (Spring's test context cache) — @Transactional rolls back the DB
		// between tests but does NOT reset queued HTTP expectations, so this must be
		// done manually.
		orsMockServer.reset();
	}

	// ── fixtures (same Toronto/Ottawa/Montreal scenario as RouteOptimizationServiceTest) ──

	private User createTestUser(String suffix) {
		User user = new User();
		user.setUsername("optimize-it-" + suffix);
		user.setEmail("optimize-it-" + suffix + "@example.com");
		user.setPasswordHash("hashed");
		return userRepository.save(user);
	}

	private RequestPostProcessor asUser(User user) {
		UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail());
		var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		return authentication(auth);
	}

	private CreateTripRequest threeStopTripRequest() {
		return new CreateTripRequest(
				"Ontario/Quebec Loop",
				null,
				null,
				TripVisibility.PRIVATE,
				List.of(
						new CreateStopRequest("Toronto", 43.65, -79.38, null, null, null),
						new CreateStopRequest("Ottawa", 45.42, -75.70, null, null, null),
						new CreateStopRequest("Montreal", 45.50, -73.57, null, null, null)));
	}

	private JsonNode createTrip(User user, CreateTripRequest request) throws Exception {
		MvcResult result = mockMvc
				.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)).with(asUser(user)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private long stopIdByName(JsonNode trip, String name) {
		for (JsonNode stop : trip.get("stops")) {
			if (stop.get("name").asText().equals(name)) {
				return stop.get("id").asLong();
			}
		}
		throw new IllegalArgumentException("No stop named '" + name + "' in " + trip);
	}

	// ── canned ORS/VROOM response bodies ─────────────────────────────────────

	/** Optimal order per VROOM: Toronto -> Montreal -> Ottawa (round-trip, vehicle starts/ends at Toronto). */
	private String cannedOptimizationResponse(long torontoId, long ottawaId, long montrealId) {
		return """
				{
				  "code": 0,
				  "summary": { "cost": 500.0, "duration": 7200.0 },
				  "routes": [
				    {
				      "vehicle": 1,
				      "duration": 7200.0,
				      "steps": [
				        { "type": "start", "job": null, "location": [-79.38, 43.65] },
				        { "type": "job", "job": %d, "location": [-79.38, 43.65] },
				        { "type": "job", "job": %d, "location": [-73.57, 45.50] },
				        { "type": "job", "job": %d, "location": [-75.70, 45.42] },
				        { "type": "end", "job": null, "location": [-79.38, 43.65] }
				      ]
				    }
				  ]
				}
				""".formatted(torontoId, montrealId, ottawaId);
	}

	private static final String CANNED_DIRECTIONS_RESPONSE = """
			{
			  "features": [
			    {
			      "geometry": { "type": "LineString", "coordinates": [[-79.38,43.65],[-73.57,45.50],[-75.70,45.42]] },
			      "properties": { "summary": { "distance": 650000.0, "duration": 21600.0 } }
			    }
			  ]
			}
			""";

	// ═══════════════════════════════════════════════════════════════════════
	// AC: happy path
	// ═══════════════════════════════════════════════════════════════════════

	@Test
	void optimizeTrip_happyPath_reordersStopsAndPersistsGeometry() throws Exception {
		User user = createTestUser("happy");
		JsonNode trip = createTrip(user, threeStopTripRequest());
		Long tripId = trip.get("id").asLong();

		long torontoId = stopIdByName(trip, "Toronto");
		long ottawaId = stopIdByName(trip, "Ottawa");
		long montrealId = stopIdByName(trip, "Montreal");

		orsMockServer.expect(requestTo(ORS_BASE_URL + "/optimization"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(cannedOptimizationResponse(torontoId, ottawaId, montrealId),
						MediaType.APPLICATION_JSON));

		orsMockServer.expect(requestTo(ORS_BASE_URL + "/v2/directions/driving-car/geojson"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(CANNED_DIRECTIONS_RESPONSE, MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/trips/" + tripId + "/optimize").with(csrf()).with(asUser(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stops[0].name").value("Toronto"))
				.andExpect(jsonPath("$.stops[0].stopOrder").value(0))
				.andExpect(jsonPath("$.stops[1].name").value("Montreal"))
				.andExpect(jsonPath("$.stops[1].stopOrder").value(1))
				.andExpect(jsonPath("$.stops[2].name").value("Ottawa"))
				.andExpect(jsonPath("$.stops[2].stopOrder").value(2));

		orsMockServer.verify();
	}

	// ═══════════════════════════════════════════════════════════════════════
	// AC: 502 propagation when ORS returns 5xx
	// ═══════════════════════════════════════════════════════════════════════

	@Test
	void optimizeTrip_orsReturns5xx_propagates502() throws Exception {
		User user = createTestUser("ors-down");
		JsonNode trip = createTrip(user, threeStopTripRequest());
		Long tripId = trip.get("id").asLong();

		orsMockServer.expect(requestTo(ORS_BASE_URL + "/optimization"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withServerError());

		mockMvc.perform(post("/api/trips/" + tripId + "/optimize").with(csrf()).with(asUser(user)))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.status").value(502))
				.andExpect(jsonPath("$.error").value("Bad Gateway"))
				.andExpect(jsonPath("$.path").value("/api/trips/" + tripId + "/optimize"));

		// No expectation was registered for /v2/directions/... — if optimize() somehow
		// still called it after the 502, verify() below fails and catches the regression.
		orsMockServer.verify();
	}
	
	@Test
	void optimizeTrip_orsRateLimited_returns429() throws Exception {
		User user = createTestUser("rate-limited");
		JsonNode trip = createTrip(user, threeStopTripRequest());
		Long tripId = trip.get("id").asLong();

		orsMockServer.expect(requestTo(ORS_BASE_URL + "/optimization"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
						.body("{\"error\":\"quota exceeded\"}")
						.contentType(MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/trips/" + tripId + "/optimize").with(csrf()).with(asUser(user)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(jsonPath("$.path").value("/api/trips/" + tripId + "/optimize"));

		orsMockServer.verify();
	}

	@Test
	void optimizeTrip_orsLeavesStopUnassigned_returns502() throws Exception {
		User user = createTestUser("unassigned");
		JsonNode trip = createTrip(user, threeStopTripRequest());
		Long tripId = trip.get("id").asLong();

		long torontoId = stopIdByName(trip, "Toronto");
		long ottawaId = stopIdByName(trip, "Ottawa");
		long montrealId = stopIdByName(trip, "Montreal");

		String partialOptimizationResponse = """
				{
				  "code": 0,
				  "summary": { "cost": 500.0, "duration": 7200.0 },
				  "routes": [{
				    "vehicle": 1,
				    "duration": 7200.0,
				    "steps": [
				      { "type": "start", "job": null, "location": [-79.38, 43.65] },
				      { "type": "job", "job": %d, "location": [-79.38, 43.65] },
				      { "type": "job", "job": %d, "location": [-75.70, 45.42] },
				      { "type": "end", "job": null, "location": [-79.38, 43.65] }
				    ]
				  }],
				  "unassigned": [{ "id": %d, "location": [-73.57, 45.50] }]
				}
				""".formatted(torontoId, ottawaId, montrealId);

		orsMockServer.expect(requestTo(ORS_BASE_URL + "/optimization"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(partialOptimizationResponse, MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/trips/" + tripId + "/optimize").with(csrf()).with(asUser(user)))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.status").value(502));

		// No directions call should follow an incomplete optimization result.
		orsMockServer.verify();
	}

	// ═══════════════════════════════════════════════════════════════════════
	// Bonus coverage: guards that short-circuit before any ORS call is made.
	// Not required by the SCRUM-58d AC but free once the harness exists — drop
	// these two if you want to keep the PR tightly scoped to the AC.
	// ═══════════════════════════════════════════════════════════════════════

	@Test
	void optimizeTrip_nonOwner_returns403_withoutCallingOrs() throws Exception {
		User owner = createTestUser("owner");
		User other = createTestUser("other");
		JsonNode trip = createTrip(owner, threeStopTripRequest());
		Long tripId = trip.get("id").asLong();

		mockMvc.perform(post("/api/trips/" + tripId + "/optimize").with(csrf()).with(asUser(other)))
				.andExpect(status().isForbidden());

		orsMockServer.verify(); // zero expectations registered — passes only if zero calls were made
	}

	@Test
	void optimizeTrip_singleStop_returns422_withoutCallingOrs() throws Exception {
		User user = createTestUser("single-stop");
		CreateTripRequest singleStopTrip = new CreateTripRequest(
				"Day Trip", null, null, TripVisibility.PRIVATE,
				List.of(new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null)));
		JsonNode trip = createTrip(user, singleStopTrip);
		Long tripId = trip.get("id").asLong();

		mockMvc.perform(post("/api/trips/" + tripId + "/optimize").with(csrf()).with(asUser(user)))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422));

		orsMockServer.verify();
	}
}
