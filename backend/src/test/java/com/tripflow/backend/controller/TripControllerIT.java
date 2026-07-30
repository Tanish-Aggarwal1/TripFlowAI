package com.tripflow.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.UpdateTripRequest;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class TripControllerIT {

	@Autowired
	private com.tripflow.backend.security.JwtService jwtService;
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private final ObjectMapper objectMapper = new ObjectMapper();

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

	private CreateTripRequest sampleTripRequest(String title, TripVisibility visibility) {
		CreateStopRequest stop = new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null);
		return new CreateTripRequest(title, null, null, visibility, List.of(stop));
	}

	private Long createTrip(User user, CreateTripRequest request) throws Exception {
		MvcResult result = mockMvc
				.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)).with(asUser(user)))
				.andExpect(status().isCreated()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	@Test
	void createTrip_andRetrieveIt_persistsAndReloadsCorrectly() throws Exception {
		User user = createTestUser("owner1");
		CreateTripRequest tripRequest = sampleTripRequest("Weekend Trip", TripVisibility.PRIVATE);

		MvcResult createResult = mockMvc
				.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(tripRequest)).with(asUser(user)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Weekend Trip"))
				.andExpect(jsonPath("$.stops[0].name").value("Cottage")).andReturn();

		Long tripId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(get("/api/trips/" + tripId).with(csrf()).with(asUser(user))).andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Weekend Trip"));
	}

	@Test
	void createTrip_invalidRequest_returns400WithValidationErrors() throws Exception {
		User user = createTestUser("invalidreq");

		CreateTripRequest tripRequest = new CreateTripRequest("", null, null, TripVisibility.PRIVATE, List.of());

		mockMvc.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(tripRequest)).with(asUser(user)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.fieldErrors").isArray());
	}

	@Test
	void getTrip_privateTripAsNonOwner_returns403() throws Exception {
		User owner = createTestUser("privowner");
		User other = createTestUser("privother");

		CreateTripRequest tripRequest = sampleTripRequest("Private Trip", TripVisibility.PRIVATE);
		Long tripId = createTrip(owner, tripRequest);

		mockMvc.perform(get("/api/trips/" + tripId).with(csrf()).with(asUser(other))).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403)).andExpect(jsonPath("$.error").value("Forbidden"));
	}

	@Test
	void listTrips_returnsOnlyRequestersTrips() throws Exception {
		User user = createTestUser("listowner");
		createTrip(user, sampleTripRequest("User's Trip", TripVisibility.PRIVATE));

		mockMvc.perform(get("/api/trips").with(csrf()).with(asUser(user))).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].title").value("User's Trip"))
				.andExpect(jsonPath("$.content[0].stopCount").value(1))
				.andExpect(jsonPath("$.content[0].stops").doesNotExist())
				.andExpect(jsonPath("$.page.totalElements").value(1));
	}

	@Test
	void listTrips_pageSizeParam_boundsResultsAndReportsTotalPages() throws Exception {
		User user = createTestUser("pageowner");
		createTrip(user, sampleTripRequest("Trip One", TripVisibility.PRIVATE));
		createTrip(user, sampleTripRequest("Trip Two", TripVisibility.PRIVATE));
		createTrip(user, sampleTripRequest("Trip Three", TripVisibility.PRIVATE));

		mockMvc.perform(get("/api/trips?page=0&size=2").with(csrf()).with(asUser(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.page.size").value(2))
				.andExpect(jsonPath("$.page.totalElements").value(3))
				.andExpect(jsonPath("$.page.totalPages").value(2));
	}

	@Test
	void updateTrip_nonOwner_returns403() throws Exception {
		User owner = createTestUser("updateowner");
		User other = createTestUser("updateother");

		CreateTripRequest tripRequest = sampleTripRequest("Original", TripVisibility.PRIVATE);
		Long tripId = createTrip(owner, tripRequest);

		CreateStopRequest stop = new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null);
		UpdateTripRequest updateRequest = new UpdateTripRequest("Hijacked", null, null, TripVisibility.PRIVATE,
				List.of(stop));

		mockMvc.perform(put("/api/trips/" + tripId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(updateRequest)).with(asUser(other)))
				.andExpect(status().isForbidden());
	}

	@Test
	void deleteTrip_owner_returns204_thenGetReturns404() throws Exception {
		User user = createTestUser("deleteowner");
		Long tripId = createTrip(user, sampleTripRequest("To Delete", TripVisibility.PRIVATE));

		mockMvc.perform(delete("/api/trips/" + tripId).with(csrf()).with(asUser(user)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/trips/" + tripId).with(csrf()).with(asUser(user))).andExpect(status().isNotFound());
	}

	@Test
	void deleteTrip_cascadesToStops_removesAllStopRows() throws Exception {
		// SCRUM-68 regression: deleting a trip must cascade to its stop rows. The existing
		// deleteTrip_owner test only asserts the trip is gone (404 on re-read); this one
		// verifies at the DB level that no orphan stop rows linger for the deleted trip.
		User user = createTestUser("cascadedelete");
		CreateStopRequest stop1 = new CreateStopRequest("Ottawa", 45.42, -75.70, null, null, null);
		CreateStopRequest stop2 = new CreateStopRequest("Toronto", 43.65, -79.38, null, null, null);
		CreateTripRequest tripRequest = new CreateTripRequest(
				"Multi-Stop Trip", null, null, TripVisibility.PRIVATE, List.of(stop1, stop2));
		Long tripId = createTrip(user, tripRequest);

		Integer stopsBefore = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM stops WHERE trip_id = ?", Integer.class, tripId);
		org.assertj.core.api.Assertions.assertThat(stopsBefore).isEqualTo(2);

		mockMvc.perform(delete("/api/trips/" + tripId).with(csrf()).with(asUser(user)))
				.andExpect(status().isNoContent());

		// tripRepository.delete marks the trip for removal; Hibernate only issues the
		// DELETE (and the FK ON DELETE CASCADE that removes stops) on flush. Force it
		// before querying, since the raw JdbcTemplate count bypasses Hibernate's auto-flush.
		entityManager.flush();

		Integer stopsAfter = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM stops WHERE trip_id = ?", Integer.class, tripId);
		org.assertj.core.api.Assertions.assertThat(stopsAfter).isZero();
	}

	@Test
	void getTrip_nonExistentId_returns404() throws Exception {
		User user = createTestUser("getnotfound");

		mockMvc.perform(get("/api/trips/999999").with(csrf()).with(asUser(user)))
				.andExpect(status().isNotFound());
	}

	@Test
	void deleteTrip_nonOwner_returns403() throws Exception {
		User owner = createTestUser("delowner");
		User other = createTestUser("delother");

		Long tripId = createTrip(owner, sampleTripRequest("Not Yours", TripVisibility.PRIVATE));

		mockMvc.perform(delete("/api/trips/" + tripId).with(csrf()).with(asUser(other)))
				.andExpect(status().isForbidden());
	}

	@Test
	void listTrips_noAuthentication_returns401ViaJsonEntryPoint() throws Exception {
		// Updated after SCRUM-100 (REF-11) landed its custom AuthenticationEntryPoint —
		// unauthenticated requests now correctly return 401, not the pre-REF-11 default
		// of 403. Was named ..._rejectedByDefaultEntryPoint asserting isForbidden().
		mockMvc.perform(get("/api/trips").with(csrf()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void createTrip_withRealJwt_authenticatesThroughFilterAndPersists() throws Exception {
		User user = createTestUser("realjwt");
		String token = jwtService.generateToken(user.getId(), user.getEmail());

		CreateTripRequest tripRequest = sampleTripRequest("Real JWT Trip", TripVisibility.PRIVATE);

		mockMvc.perform(post("/api/trips")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.header("Authorization", "Bearer " + token)
						.content(objectMapper.writeValueAsString(tripRequest)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Real JWT Trip"));
	}
}
