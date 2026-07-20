package com.tripflow.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class StopControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private User createTestUser(String suffix) {
		User user = new User();
		user.setUsername("stoptest-" + suffix);
		user.setEmail("stoptest-" + suffix + "@example.com");
		user.setPasswordHash("hashed");
		return userRepository.save(user);
	}

	private RequestPostProcessor asUser(User user) {
		UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail());
		var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		return authentication(auth);
	}

	private Long createTripAsUser(User user) throws Exception {
		CreateStopRequest stop = new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null);
		CreateTripRequest tripRequest = new CreateTripRequest("Muskoka Trip", null, null, TripVisibility.PRIVATE,
				List.of(stop));

		MvcResult result = mockMvc
				.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(tripRequest)).with(asUser(user)))
				.andExpect(status().isCreated()).andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	@Test
	void addStop_owner_returns201() throws Exception {
		User owner = createTestUser("addowner");
		Long tripId = createTripAsUser(owner);

		CreateStopRequest newStop = new CreateStopRequest("Gas Station", 44.5, -79.6, null, null, null);

		mockMvc.perform(post("/api/trips/" + tripId + "/stops").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(newStop)).with(asUser(owner))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.stopOrder").value(1)).andExpect(jsonPath("$.name").value("Gas Station"));
	}

	@Test
	void addStop_nonOwner_returns403() throws Exception {
		User owner = createTestUser("nonownerowner");
		User other = createTestUser("nonownerother");
		Long tripId = createTripAsUser(owner);

		CreateStopRequest newStop = new CreateStopRequest("Gas Station", 44.5, -79.6, null, null, null);

		mockMvc.perform(post("/api/trips/" + tripId + "/stops").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(newStop)).with(asUser(other)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void listStops_owner_returnsStops() throws Exception {
		User owner = createTestUser("listowner");
		Long tripId = createTripAsUser(owner);

		mockMvc.perform(get("/api/trips/" + tripId + "/stops").with(csrf()).with(asUser(owner)))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Cottage"));
	}

	@Test
	void deleteStop_missingStop_returns404() throws Exception {
		User owner = createTestUser("deleteowner");
		Long tripId = createTripAsUser(owner);

		mockMvc.perform(delete("/api/trips/" + tripId + "/stops/999999").with(csrf()).with(asUser(owner)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
	}
}
