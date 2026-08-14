package com.tripflow.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.repository.PlaceRepository;
import com.tripflow.backend.repository.TripRepository;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

/**
 * SCRUM-306 regression coverage: two users creating trips that reference the same Mapbox
 * place at the same instant used to turn one of them into a 500 — the old
 * {@code createPlace()} caught the {@code DataIntegrityViolationException} from a duplicate
 * insert and re-read the existing row to recover, but that read ran in the same transaction
 * as the failed insert. A real Postgres constraint violation aborts the whole transaction, so
 * the recovery read failed too ({@code current transaction is aborted, commands ignored until
 * end of transaction block}). {@link com.tripflow.backend.service.PlaceResolutionServiceTest}
 * only proves the recovery logic against a mocked repository, which never exercises a real
 * aborted transaction and so could not have caught this. This test forces a genuine race
 * against Testcontainers Postgres.
 *
 * <p>No class-level {@code @Transactional} — each request must commit on its own connection
 * for the two threads to genuinely race at the database, same reasoning as
 * {@link AuthConcurrencyIT} / {@link RouteOptimizationConcurrencyIT}. Cleans up everything it
 * creates in {@link #cleanUp()} regardless — good hygiene in a shared Testcontainers Postgres
 * instance, even though the fix itself ({@code PlaceRepository.insertIgnoringConflict},
 * {@code ON CONFLICT DO NOTHING}) keeps the insert inside the caller's own transaction rather
 * than committing independently.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PlaceResolutionConcurrencyIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private PlaceRepository placeRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final List<Long> createdUserIds = new CopyOnWriteArrayList<>();
	private final List<Long> createdTripIds = new CopyOnWriteArrayList<>();
	private String externalPlaceId;

	@AfterEach
	void cleanUp() {
		// Deleting a Trip cascades to its Stops (Trip.stops is CascadeType.ALL +
		// orphanRemoval), so the Place is unreferenced by the time we delete it.
		createdTripIds.forEach(id -> tripRepository.findById(id).ifPresent(tripRepository::delete));
		createdUserIds.forEach(id -> userRepository.findById(id).ifPresent(userRepository::delete));
		if (externalPlaceId != null) {
			placeRepository.findByExternalPlaceId(externalPlaceId).ifPresent(placeRepository::delete);
		}
	}

	private User createTestUser(String suffix) {
		User user = new User();
		user.setUsername("placerace-" + suffix);
		user.setEmail("placerace-" + suffix + "@tripflow.com");
		user.setPasswordHash("hashed");
		User saved = userRepository.save(user);
		createdUserIds.add(saved.getId());
		return saved;
	}

	private RequestPostProcessor asUser(User user) {
		UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail());
		var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		return authentication(auth);
	}

	private int createTripWithStop(User user, String externalPlaceId, CountDownLatch ready, CountDownLatch go)
			throws Exception {
		CreateStopRequest stop = new CreateStopRequest("Shared Landmark", 45.0, -75.0, null, externalPlaceId, null);
		CreateTripRequest tripRequest = new CreateTripRequest("Race Trip", null, null, TripVisibility.PRIVATE,
				List.of(stop));

		ready.countDown();
		assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();

		MvcResult result = mockMvc
				.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(tripRequest)).with(asUser(user)))
				.andReturn();
		int status = result.getResponse().getStatus();
		if (status == 201) {
			createdTripIds.add(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong());
		}
		return status;
	}

	@Test
	void createTrip_concurrentRequestsReferencingSamePlace_neitherReturns500() throws Exception {
		User userA = createTestUser("a-" + System.nanoTime());
		User userB = createTestUser("b-" + System.nanoTime());
		externalPlaceId = "race-place-" + System.nanoTime();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		List<Integer> statuses;
		try {
			Callable<Integer> first = () -> createTripWithStop(userA, externalPlaceId, ready, go);
			Callable<Integer> second = () -> createTripWithStop(userB, externalPlaceId, ready, go);

			Future<Integer> a = executor.submit(first);
			Future<Integer> b = executor.submit(second);

			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			go.countDown();

			statuses = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
		}

		// Before SCRUM-306: the loser of the race got a 500 instead of a successful 201
		// reusing the winner's Place row.
		assertThat(statuses).containsExactly(201, 201);
		assertThat(placeRepository.findByExternalPlaceId(externalPlaceId)).isPresent();
	}
}
