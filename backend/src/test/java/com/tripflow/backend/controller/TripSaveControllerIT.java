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

/**
 * End-to-end IT for POST/DELETE /api/trips/{id}/save and GET /api/trips/saved (SOCIAL-04).
 * Mirrors {@link TripLikeControllerIT}'s MockMvc harness. Exists as a separate file from
 * {@code TripSaveServiceIT} (a {@code @DataJpaTest}) because the two behaviors this class
 * pins — GET /api/trips/saved resolving to the literal-segment handler rather than being
 * swallowed by the {@code {id}} path template, and an unauthenticated request returning 401
 * — are HTTP-routing/security-filter concerns that a repository-slice test cannot exercise.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class TripSaveControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User createTestUser(String suffix) {
        User user = new User();
        user.setUsername("save-" + suffix);
        user.setEmail("save-" + suffix + "@example.com");
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
    void saveTrip_publicTripOtherUser_returns200() throws Exception {
        User owner = createTestUser("owner1");
        User saver = createTestUser("saver1");
        Long tripId = createTrip(owner, "Public Trip", TripVisibility.PUBLIC);

        mockMvc.perform(post("/api/trips/" + tripId + "/save").with(csrf()).with(asUser(saver)))
                .andExpect(status().isOk());
    }

    @Test
    void saveTrip_privateTripOtherUser_returns404() throws Exception {
        User owner = createTestUser("owner2");
        User other = createTestUser("other2");
        Long tripId = createTrip(owner, "Private Trip", TripVisibility.PRIVATE);

        mockMvc.perform(post("/api/trips/" + tripId + "/save").with(csrf()).with(asUser(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsaveTrip_neverSaved_isIdempotentReturns200() throws Exception {
        User owner = createTestUser("owner3");
        User other = createTestUser("other3");
        Long tripId = createTrip(owner, "Public Trip", TripVisibility.PUBLIC);

        mockMvc.perform(delete("/api/trips/" + tripId + "/save").with(csrf()).with(asUser(other)))
                .andExpect(status().isOk());
    }

    @Test
    void listSavedTrips_resolvesToSavedListHandler_notSwallowedByIdTemplate() throws Exception {
        User owner = createTestUser("owner4");
        User saver = createTestUser("saver4");
        Long tripId = createTrip(owner, "Public Trip", TripVisibility.PUBLIC);
        mockMvc.perform(post("/api/trips/" + tripId + "/save").with(csrf()).with(asUser(saver)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/trips/saved").with(asUser(saver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(tripId));
    }

    @Test
    void listSavedTrips_savedByAnotherUser_neverAppearsInCallersList() throws Exception {
        User owner = createTestUser("owner5");
        User userA = createTestUser("usera5");
        User userB = createTestUser("userb5");
        Long tripId = createTrip(owner, "Public Trip", TripVisibility.PUBLIC);

        mockMvc.perform(post("/api/trips/" + tripId + "/save").with(csrf()).with(asUser(userA)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/trips/saved").with(asUser(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void listSavedTrips_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/trips/saved"))
                .andExpect(status().isUnauthorized());
    }
}
