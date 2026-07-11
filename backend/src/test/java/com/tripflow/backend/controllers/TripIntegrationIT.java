package com.tripflow.backend.controllers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.beans.User;
import com.tripflow.backend.beans.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.UpdateTripRequest;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class TripIntegrationIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private String createTestUser(String suffix) {
		User user = new User();
		user.setUsername("integtest-" + suffix);
		user.setEmail("integtest-" + suffix + "@example.com");
		user.setPasswordHash("hashed");
		Long id = userRepository.save(user).getId();
		return id.toString();
	}

	private CreateTripRequest sampleTripRequest(String title, TripVisibility visibility) {
		CreateStopRequest stop = new CreateStopRequest();
		stop.setName("Cottage");
		stop.setLatitude(45.0);
		stop.setLongitude(-79.9);

		CreateTripRequest request = new CreateTripRequest();
		request.setTitle(title);
		request.setVisibility(visibility);
		request.setStops(List.of(stop));
		return request;
	}

	private Long createTrip(String userId, CreateTripRequest request) throws Exception {
		MvcResult result = mockMvc
				.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)).with(user(userId)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	@Test
	void createTrip_andRetrieveIt_persistsAndReloadsCorrectly() throws Exception {
		String userId = createTestUser("owner1");
		CreateTripRequest tripRequest = sampleTripRequest("Weekend Trip", TripVisibility.PRIVATE);

		MvcResult createResult = mockMvc
				.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(tripRequest)).with(user(userId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Weekend Trip"))
				.andExpect(jsonPath("$.stops[0].name").value("Cottage"))
				.andReturn();

		Long tripId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(get("/api/trips/" + tripId).with(csrf()).with(user(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Weekend Trip"));
	}

	@Test
	void createTrip_invalidRequest_returns400WithValidationErrors() throws Exception {
		String userId = createTestUser("invalidreq");

		CreateTripRequest tripRequest = new CreateTripRequest();
		tripRequest.setTitle("");
		tripRequest.setVisibility(TripVisibility.PRIVATE);
		tripRequest.setStops(List.of());

		mockMvc.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(tripRequest)).with(user(userId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.fieldErrors").isArray());
	}

	@Test
	void getTrip_privateTripAsNonOwner_returns403() throws Exception {
		String ownerId = createTestUser("privowner");
		String otherId = createTestUser("privother");

		CreateTripRequest tripRequest = sampleTripRequest("Private Trip", TripVisibility.PRIVATE);
		Long tripId = createTrip(ownerId, tripRequest);

		mockMvc.perform(get("/api/trips/" + tripId).with(csrf()).with(user(otherId)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.error").value("Forbidden"));
	}

	@Test
	void listTrips_returnsOnlyRequestersTrips() throws Exception {
		String userId = createTestUser("listowner");
		createTrip(userId, sampleTripRequest("User's Trip", TripVisibility.PRIVATE));

		mockMvc.perform(get("/api/trips").with(csrf()).with(user(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].title").value("User's Trip"));
	}

	@Test
	void updateTrip_nonOwner_returns403() throws Exception {
		String ownerId = createTestUser("updateowner");
		String otherId = createTestUser("updateother");

		CreateTripRequest tripRequest = sampleTripRequest("Original", TripVisibility.PRIVATE);
		Long tripId = createTrip(ownerId, tripRequest);

		CreateStopRequest stop = new CreateStopRequest();
		stop.setName("Cottage");
		stop.setLatitude(45.0);
		stop.setLongitude(-79.9);

		UpdateTripRequest updateRequest = new UpdateTripRequest();
		updateRequest.setTitle("Hijacked");
		updateRequest.setVisibility(TripVisibility.PRIVATE);
		updateRequest.setStops(List.of(stop));

		mockMvc.perform(put("/api/trips/" + tripId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(updateRequest)).with(user(otherId)))
				.andExpect(status().isForbidden());
	}

	@Test
	void deleteTrip_owner_returns204_thenGetReturns404() throws Exception {
		String userId = createTestUser("deleteowner");
		Long tripId = createTrip(userId, sampleTripRequest("To Delete", TripVisibility.PRIVATE));

		mockMvc.perform(delete("/api/trips/" + tripId).with(csrf()).with(user(userId)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/trips/" + tripId).with(csrf()).with(user(userId)))
				.andExpect(status().isNotFound());
	}
}
