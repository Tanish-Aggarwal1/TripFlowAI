package com.tripflow.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.client.ors.OrsClient;
import com.tripflow.backend.client.ors.OrsDirectionsResponse;
import com.tripflow.backend.client.ors.OrsOptimizationRequest;
import com.tripflow.backend.client.ors.OrsOptimizationResponse;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

/**
 * SCRUM-210 / SCRUM-226 regression coverage: before the transaction-boundary fix,
 * {@code RouteOptimizationService.optimize()} held its Hikari connection for the entire
 * ORS round trip because the whole method was {@code @Transactional}. With a small pool,
 * a handful of concurrent optimize calls pinned every connection for the ORS call
 * duration and starved unrelated endpoints — including {@code /actuator/health} — until
 * Hikari's connection-timeout elapsed.
 *
 * <p>This test caps the pool at 2, drives 3 concurrent {@code /optimize} calls whose
 * mocked {@link OrsClient} blocks until released, and asserts {@code /actuator/health}
 * stays fast while all three are genuinely in flight — which only holds if the load and
 * persist transactions release their connections before/after the ORS call, not across it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.hikari.maximum-pool-size=2",
		"spring.datasource.hikari.connection-timeout=1000"
})
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RouteOptimizationConcurrencyIT {

	private static final int CONCURRENT_REQUESTS = 3; // > hikari.maximum-pool-size (2)

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@MockitoBean
	private OrsClient orsClient;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private User createTestUser(String suffix) {
		User user = new User();
		user.setUsername("concurrency-" + suffix);
		user.setEmail("concurrency-" + suffix + "@example.com");
		user.setPasswordHash("hashed");
		return userRepository.save(user);
	}

	private RequestPostProcessor asUser(User user) {
		UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail());
		var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		return authentication(auth);
	}

	private Long createTwoStopTrip(User user, String title) throws Exception {
		CreateTripRequest request = new CreateTripRequest(title, null, null, TripVisibility.PRIVATE,
				List.of(new CreateStopRequest("A", 43.65, -79.38, null, null, null),
						new CreateStopRequest("B", 45.42, -75.70, null, null, null)));

		MvcResult result = mockMvc
				.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)).with(asUser(user)))
				.andExpect(status().isCreated())
				.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	@Test
	void concurrentOptimizeCalls_doNotStarveUnrelatedRequestsOfConnections() throws Exception {
		User user = createTestUser("main");
		List<Long> tripIds = new ArrayList<>();
		for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
			tripIds.add(createTwoStopTrip(user, "Trip " + i));
		}

		CountDownLatch allInFlight = new CountDownLatch(CONCURRENT_REQUESTS);
		CountDownLatch releaseOrs = new CountDownLatch(1);

		// Blocks every optimize() call inside the ORS call until the test releases it —
		// this is what "in flight" means below, and it's the window during which a
		// held-open connection would starve everything else.
		given(orsClient.optimize(any())).willAnswer(invocation -> {
			allInFlight.countDown();
			assertThat(releaseOrs.await(10, TimeUnit.SECONDS)).isTrue();

			OrsOptimizationRequest req = invocation.getArgument(0);
			List<OrsOptimizationResponse.Step> steps = new ArrayList<>();
			steps.add(new OrsOptimizationResponse.Step("start", null, req.vehicles().get(0).start()));
			for (OrsOptimizationRequest.Job job : req.jobs()) {
				steps.add(new OrsOptimizationResponse.Step("job", job.id(), job.location()));
			}
			steps.add(new OrsOptimizationResponse.Step("end", null, req.vehicles().get(0).end()));

			return new OrsOptimizationResponse(0, new OrsOptimizationResponse.Summary(1.0, 1.0),
					List.of(new OrsOptimizationResponse.Route(1L, 1.0, steps)));
		});
		given(orsClient.getDirections(any())).willReturn(new OrsDirectionsResponse(List.of(
				new OrsDirectionsResponse.Feature(
						new OrsDirectionsResponse.Geometry("LineString",
								List.of(List.of(0.0, 0.0), List.of(1.0, 1.0))),
						new OrsDirectionsResponse.Properties(new OrsDirectionsResponse.Summary(1000.0, 60.0))))));

		ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
		try {
			List<Future<Integer>> futures = tripIds.stream()
					.map(tripId -> executor.submit(() -> mockMvc
							.perform(post("/api/trips/" + tripId + "/optimize").with(csrf()).with(asUser(user)))
							.andReturn().getResponse().getStatus()))
					.toList();

			assertThat(allInFlight.await(10, TimeUnit.SECONDS))
					.as("all %d optimize() calls should reach the mocked ORS call", CONCURRENT_REQUESTS)
					.isTrue();

			long start = System.nanoTime();
			mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
			long elapsedMs = (System.nanoTime() - start) / 1_000_000;
			assertThat(elapsedMs)
					.as("/actuator/health must stay responsive while %d optimize() calls are mid-flight "
							+ "against a 2-connection pool", CONCURRENT_REQUESTS)
					.isLessThan(1000);

			releaseOrs.countDown();

			for (Future<Integer> future : futures) {
				assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(200);
			}
		} finally {
			executor.shutdownNow();
		}
	}
}
