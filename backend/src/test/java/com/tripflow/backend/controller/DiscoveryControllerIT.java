package com.tripflow.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

/** End-to-end IT for GET /api/discovery/search (SCRUM-163). 
 * End-to-end IT for GET /api/discovery/trips (SCRUM-160): unauthenticated public feed,
 * PUBLIC-only filtering, default sort, and the shared PagedModel contract from SCRUM-110.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class DiscoveryControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private User createTestUser(String suffix) {
		User user = new User();
		user.setUsername("discovery-" + suffix);
		user.setEmail("discovery-" + suffix + "@example.com");
		user.setPasswordHash("hashed");
		return userRepository.save(user);
	}

	private RequestPostProcessor asUser(User user) {
		UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail());
		var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		return authentication(auth);
	}

	private void createTrip(User owner, String title, TripVisibility visibility, List<String> tags) throws Exception {
    CreateTripRequest request = new CreateTripRequest(
            title,
            null,
            tags,
            visibility,
				List.of(new CreateStopRequest("Byward Market", 45.4285, -75.6935, null, null, null)));

		mockMvc.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)).with(asUser(owner)))
				.andExpect(status().isCreated());
	}

	@Test
void search_noAuth_missingQParam_returns400() throws Exception {
    mockMvc.perform(get("/api/discovery/search"))
            .andExpect(status().isBadRequest());
}

@Test
void search_blankQParam_returns400() throws Exception {
    mockMvc.perform(get("/api/discovery/search").param("q", "   "))
            .andExpect(status().isBadRequest());
}

@Test
void search_qOverMaxLength_returns400WithoutQuerying() throws Exception {
    // SCRUM-493: @Size(max = 100) on 'q' must reject before hitting the repository.
    String tooLong = "a".repeat(101);
    mockMvc.perform(get("/api/discovery/search").param("q", tooLong))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
}

@Test
void search_qAtMaxLength_returns200() throws Exception {
    String exactly100 = "a".repeat(100);
    mockMvc.perform(get("/api/discovery/search").param("q", exactly100))
            .andExpect(status().isOk());
}

@Test
void search_noAuth_titleMatch_returnsPublicTripsOnly() throws Exception {
    User owner = createTestUser("owner1");

    createTrip(owner, "Ottawa Weekend", TripVisibility.PUBLIC, null);
    createTrip(owner, "Ottawa Secret Trip", TripVisibility.PRIVATE, null);
    createTrip(owner, "Unrelated Public Trip", TripVisibility.PUBLIC, null);

    mockMvc.perform(get("/api/discovery/search").param("q", "ottawa"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Ottawa Weekend"));
}

  @Test
  void listPublicTrips_noAuth_returnsOnlyPublicTrips() throws Exception {
      User owner = createTestUser("owner");
  
      createTrip(owner, "Public One", TripVisibility.PUBLIC, null);
      createTrip(owner, "Public Two", TripVisibility.PUBLIC, null);
      createTrip(owner, "Private One", TripVisibility.PRIVATE, null);
  
      mockMvc.perform(get("/api/discovery/trips"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.content.length()").value(2))
              .andExpect(jsonPath("$.content[*].visibility",
                      org.hamcrest.Matchers.everyItem(
                              org.hamcrest.Matchers.is("PUBLIC"))))
              .andExpect(jsonPath("$.content[*].stops").doesNotExist());
  }

}
