package com.tripflow.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

/** End-to-end IT for POST/DELETE /api/trips/{id}/like (SCRUM-161). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class TripLikeControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private User createTestUser(String suffix) {
		User user = new User();
		user.setUsername("like-" + suffix);
		user.setEmail("like-" + suffix + "@example.com");
		user.setPasswordHash("hashed");
		return userRepository.save(user);
	}

	private RequestPostProcessor asUser(User user) {
		UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail());
		var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		return authentication(auth);
	}

	private Long createTrip(User owner, String title, TripVisibility visibility) throws Exception {
		CreateTripRequest request = new CreateTripRequest(title, null, null, visibility,
				List.of(new CreateStopRequest("Byward Market", 45.4285, -75.6935, null, null, null)));

		MvcResult result = mockMvc
				.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)).with(asUser(owner)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	@Test
	void likeTrip_publicTripOtherUser_returns200() throws Exception {
		User owner = createTestUser("owner1");
		User liker = createTestUser("liker1");
		Long tripId = createTrip(owner, "Public Trip", TripVisibility.PUBLIC);

		mockMvc.perform(post("/api/trips/" + tripId + "/like").with(csrf()).with(asUser(liker)))
				.andExpect(status().isOk());
	}

	@Test
	void likeTrip_ownPrivateTrip_returns200() throws Exception {
		User owner = createTestUser("owner2");
		Long tripId = createTrip(owner, "Private Trip", TripVisibility.PRIVATE);

		mockMvc.perform(post("/api/trips/" + tripId + "/like").with(csrf()).with(asUser(owner)))
				.andExpect(status().isOk());
	}

	@Test
	void likeTrip_privateTripOtherUser_returns404() throws Exception {
		User owner = createTestUser("owner3");
		User other = createTestUser("other3");
		Long tripId = createTrip(owner, "Private Trip", TripVisibility.PRIVATE);

		mockMvc.perform(post("/api/trips/" + tripId + "/like").with(csrf()).with(asUser(other)))
				.andExpect(status().isNotFound());
	}

	@Test
	void likeTrip_nonExistentTrip_returns404() throws Exception {
		User user = createTestUser("notfound");

		mockMvc.perform(post("/api/trips/999999/like").with(csrf()).with(asUser(user)))
				.andExpect(status().isNotFound());
	}

	@Test
	void likeTrip_calledTwice_isIdempotentAndCountsOnce() throws Exception {
		User owner = createTestUser("owner4");
		User liker = createTestUser("liker4");
		Long tripId = createTrip(owner, "Public Trip", TripVisibility.PUBLIC);

		mockMvc.perform(post("/api/trips/" + tripId + "/like").with(csrf()).with(asUser(liker)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/trips/" + tripId + "/like").with(csrf()).with(asUser(liker)))
				.andExpect(status().isOk());

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/trips/" + tripId).with(asUser(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.likeCount").value(1));
	}

	@Test
	void unlikeTrip_afterLike_decrementsCount() throws Exception {
		User owner = createTestUser("owner5");
		User liker = createTestUser("liker5");
		Long tripId = createTrip(owner, "Public Trip", TripVisibility.PUBLIC);

		mockMvc.perform(post("/api/trips/" + tripId + "/like").with(csrf()).with(asUser(liker)))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/trips/" + tripId + "/like").with(csrf()).with(asUser(liker)))
				.andExpect(status().isOk());

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/trips/" + tripId).with(asUser(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.likeCount").value(0));
	}

	@Test
	void unlikeTrip_neverLiked_isIdempotentReturns200() throws Exception {
		User owner = createTestUser("owner6");
		User other = createTestUser("other6");
		Long tripId = createTrip(owner, "Public Trip", TripVisibility.PUBLIC);

		mockMvc.perform(delete("/api/trips/" + tripId + "/like").with(csrf()).with(asUser(other)))
				.andExpect(status().isOk());
	}

	@Test
	void likeTrip_multipleUsers_countsEach() throws Exception {
		User owner = createTestUser("owner7");
		User likerA = createTestUser("likerA7");
		User likerB = createTestUser("likerB7");
		Long tripId = createTrip(owner, "Public Trip", TripVisibility.PUBLIC);

		mockMvc.perform(post("/api/trips/" + tripId + "/like").with(csrf()).with(asUser(likerA)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/trips/" + tripId + "/like").with(csrf()).with(asUser(likerB)))
				.andExpect(status().isOk());

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/trips/" + tripId).with(asUser(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.likeCount").value(2));
	}
}
