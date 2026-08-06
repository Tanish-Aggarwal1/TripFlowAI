package com.tripflow.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

/** End-to-end IT for POST /api/trips/{id}/clone (SCRUM-162). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class TripCloneControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private User createTestUser(String suffix) {
		User user = new User();
		user.setUsername("clone-" + suffix);
		user.setEmail("clone-" + suffix + "@example.com");
		user.setPasswordHash("hashed");
		return userRepository.save(user);
	}

	private RequestPostProcessor asUser(User user) {
		UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail());
		var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		return authentication(auth);
	}

	private JsonNode createTrip(User owner, String title, TripVisibility visibility,
			List<CreateStopRequest> stops) throws Exception {
		CreateTripRequest request = new CreateTripRequest(title, "A description", List.of("tag1"), visibility, stops);

		MvcResult result = mockMvc
				.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)).with(asUser(owner)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	@Test
	void cloneTrip_publicTripOtherUser_createsPrivateCopyOwnedByRequester() throws Exception {
		User owner = createTestUser("owner1");
		User cloner = createTestUser("cloner1");
		JsonNode original = createTrip(owner, "Ottawa Weekend", TripVisibility.PUBLIC,
				List.of(new CreateStopRequest("Byward Market", 45.4285, -75.6935, null, null, null)));

		mockMvc.perform(post("/api/trips/" + original.get("id").asLong() + "/clone")
						.with(csrf()).with(asUser(cloner)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Copy of Ottawa Weekend"))
				.andExpect(jsonPath("$.visibility").value("PRIVATE"))
				.andExpect(jsonPath("$.ownerId").value(cloner.getId()))
				.andExpect(jsonPath("$.stops.length()").value(1));
	}

	@Test
	void cloneTrip_ownPrivateTrip_succeeds() throws Exception {
		User owner = createTestUser("owner2");
		JsonNode original = createTrip(owner, "My Trip", TripVisibility.PRIVATE,
				List.of(new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null)));

		mockMvc.perform(post("/api/trips/" + original.get("id").asLong() + "/clone")
						.with(csrf()).with(asUser(owner)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Copy of My Trip"));
	}

	@Test
	void cloneTrip_privateTripOtherUser_returns404() throws Exception {
		User owner = createTestUser("owner3");
		User other = createTestUser("other3");
		JsonNode original = createTrip(owner, "Private Trip", TripVisibility.PRIVATE,
				List.of(new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null)));

		mockMvc.perform(post("/api/trips/" + original.get("id").asLong() + "/clone")
						.with(csrf()).with(asUser(other)))
				.andExpect(status().isNotFound());
	}

	@Test
	void cloneTrip_nonExistentTrip_returns404() throws Exception {
		User user = createTestUser("notfound");

		mockMvc.perform(post("/api/trips/999999/clone").with(csrf()).with(asUser(user)))
				.andExpect(status().isNotFound());
	}

	@Test
	void cloneTrip_multipleStops_preservesStopOrderAndSharesPlaceIds() throws Exception {
		User owner = createTestUser("owner4");
		User cloner = createTestUser("cloner4");
		JsonNode original = createTrip(owner, "Multi Stop Trip", TripVisibility.PUBLIC,
				List.of(
						new CreateStopRequest("Byward Market", 45.4285, -75.6935, null, null, null),
						new CreateStopRequest("Parliament Hill", 45.4236, -75.7003, null, null, null),
						new CreateStopRequest("Rideau Canal", 45.4215, -75.6919, null, null, null)));

		List<String> originalStopNames = List.of("Byward Market", "Parliament Hill", "Rideau Canal");
		assertThat(original.get("stops")).hasSize(3);
		for (int i = 0; i < 3; i++) {
			assertThat(original.get("stops").get(i).get("name").asText()).isEqualTo(originalStopNames.get(i));
		}

		MvcResult cloneResult = mockMvc
				.perform(post("/api/trips/" + original.get("id").asLong() + "/clone")
						.with(csrf()).with(asUser(cloner)))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode clone = objectMapper.readTree(cloneResult.getResponse().getContentAsString());

		assertThat(clone.get("stops")).hasSize(3);
		for (int i = 0; i < 3; i++) {
			JsonNode originalStop = original.get("stops").get(i);
			JsonNode cloneStop = clone.get("stops").get(i);
			assertThat(cloneStop.get("name").asText()).isEqualTo(originalStop.get("name").asText());
			assertThat(cloneStop.get("stopOrder").asInt()).isEqualTo(originalStop.get("stopOrder").asInt());
			// Same underlying Place shared, not duplicated — different Stop id, same coordinates
			// (StopResponse doesn't expose placeId directly, so lat/lng equality is the proxy
			// for "same Place row" here; the repository-level IT asserts actual placeId equality).
			assertThat(cloneStop.get("id").asLong()).isNotEqualTo(originalStop.get("id").asLong());
			assertThat(cloneStop.get("latitude").asDouble()).isEqualTo(originalStop.get("latitude").asDouble());
			assertThat(cloneStop.get("longitude").asDouble()).isEqualTo(originalStop.get("longitude").asDouble());
		}
	}
}
