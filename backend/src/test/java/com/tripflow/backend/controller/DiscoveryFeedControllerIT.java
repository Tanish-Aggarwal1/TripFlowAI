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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.StopPhoto;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.repository.StopPhotoRepository;
import com.tripflow.backend.repository.StopRepository;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

/**
 * End-to-end IT for GET /api/discovery/feed (SOCIAL-01). Covers the authentication gate
 * (06-CONTEXT.md Phase Boundary), the feed-shaped response fields D-02/D-03 need, and
 * PUBLIC-only visibility filtering. Testcontainers/MockMvc scaffolding and the
 * {@code createTrip} helper are copied from {@link DiscoveryControllerIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class DiscoveryFeedControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StopRepository stopRepository;

    @Autowired
    private StopPhotoRepository stopPhotoRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User createTestUser(String suffix) {
        User user = new User();
        user.setUsername("feed-" + suffix);
        user.setEmail("feed-" + suffix + "@example.com");
        user.setPasswordHash("hashed");
        return userRepository.save(user);
    }

    private RequestPostProcessor asUser(User user) {
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(auth);
    }

    private void createTrip(User owner, String title, String description, TripVisibility visibility,
            List<String> tags) throws Exception {
        CreateTripRequest request = new CreateTripRequest(
                title,
                description,
                tags,
                visibility,
                List.of(new CreateStopRequest("Byward Market", 45.4285, -75.6935, "55 ByWard Market Sq", null,
                        "Try the beavertails")));

        mockMvc.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)).with(asUser(owner)))
                .andExpect(status().isCreated());
    }

    @Test
    void getFeed_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/discovery/feed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/discovery/feed"));
    }

    @Test
    void getPublicTrips_noAuth_returns401() throws Exception {
        // Permit-all removal (Task 1/2) verified against an already-existing discovery path.
        mockMvc.perform(get("/api/discovery/trips"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getFeed_authenticated_returnsFeedShapedPublicTrip() throws Exception {
        User owner = createTestUser("owner1");
        User viewer = createTestUser("viewer1");

        createTrip(owner, "Ottawa Weekend", "A cozy fall trip", TripVisibility.PUBLIC, List.of("fall", "food"));

        mockMvc.perform(get("/api/discovery/feed").with(asUser(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Ottawa Weekend"))
                .andExpect(jsonPath("$.content[0].description").value("A cozy fall trip"))
                .andExpect(jsonPath("$.content[0].tags", org.hamcrest.Matchers.containsInAnyOrder("fall", "food")))
                .andExpect(jsonPath("$.content[0].ownerUsername").value(owner.getUsername()))
                .andExpect(jsonPath("$.content[0].stops").isArray())
                .andExpect(jsonPath("$.content[0].stops.length()").value(1));
    }

    @Test
    void getFeed_authenticated_excludesPrivateTrips() throws Exception {
        User owner = createTestUser("owner2");
        User viewer = createTestUser("viewer2");

        createTrip(owner, "Public Getaway", "Visible to everyone", TripVisibility.PUBLIC, null);
        createTrip(owner, "Secret Plans", "Not for the feed", TripVisibility.PRIVATE, null);

        mockMvc.perform(get("/api/discovery/feed").with(asUser(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Public Getaway"));
    }

    @Test
    void getFeed_ownerUsernameIsSeededOwner_notViewer() throws Exception {
        User owner = createTestUser("owner3");
        User viewer = createTestUser("viewer3");

        createTrip(owner, "Someone Else's Trip", null, TripVisibility.PUBLIC, null);

        mockMvc.perform(get("/api/discovery/feed").with(asUser(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].ownerUsername").value(owner.getUsername()))
                .andExpect(jsonPath("$.content[0].ownerUsername",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.equalTo(viewer.getUsername()))));
    }

    @Test
    void getFeed_zeroPhotoTrip_stillPresentWithEmptyPhotoUrls() throws Exception {
        // D-03: a trip whose stops all have zero photos is included in the feed with
        // renderable text content (non-null name/notes), not excluded and not broken.
        User owner = createTestUser("owner4");
        User viewer = createTestUser("viewer4");

        createTrip(owner, "No Photos Yet", "Still worth seeing", TripVisibility.PUBLIC, null);

        mockMvc.perform(get("/api/discovery/feed").with(asUser(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].stops[0].name").value("Byward Market"))
                .andExpect(jsonPath("$.content[0].stops[0].notes").value("Try the beavertails"))
                .andExpect(jsonPath("$.content[0].stops[0].photoUrls").isArray())
                .andExpect(jsonPath("$.content[0].stops[0].photoUrls.length()").value(0));
    }

    @Test
    void getFeed_batchedPhotoFetch_preservesCreatedAtAscendingOrder() throws Exception {
        User owner = createTestUser("owner5");
        User viewer = createTestUser("viewer5");

        CreateTripRequest request = new CreateTripRequest(
                "Multi-Stop Trip",
                null,
                null,
                TripVisibility.PUBLIC,
                List.of(
                        new CreateStopRequest("Stop A", 45.4285, -75.6935, null, null, null),
                        new CreateStopRequest("Stop B", 45.42, -75.69, null, null, null)));

        String body = mockMvc.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)).with(asUser(owner)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode tripJson = objectMapper.readTree(body);
        Long firstStopId = tripJson.get("stops").get(0).get("id").asLong();

        Stop firstStop = stopRepository.findById(firstStopId).orElseThrow();
        StopPhoto photoOne = new StopPhoto();
        photoOne.setStop(firstStop);
        photoOne.setUrl("https://res.cloudinary.com/demo/image/upload/v1/photo-one.jpg");
        stopPhotoRepository.saveAndFlush(photoOne);

        StopPhoto photoTwo = new StopPhoto();
        photoTwo.setStop(firstStop);
        photoTwo.setUrl("https://res.cloudinary.com/demo/image/upload/v1/photo-two.jpg");
        stopPhotoRepository.saveAndFlush(photoTwo);

        mockMvc.perform(get("/api/discovery/feed").with(asUser(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].stops[0].photoUrls.length()").value(2))
                .andExpect(jsonPath("$.content[0].stops[0].photoUrls[0]")
                        .value("https://res.cloudinary.com/demo/image/upload/v1/photo-one.jpg"))
                .andExpect(jsonPath("$.content[0].stops[0].photoUrls[1]")
                        .value("https://res.cloudinary.com/demo/image/upload/v1/photo-two.jpg"))
                .andExpect(jsonPath("$.content[0].stops[1].photoUrls.length()").value(0));
    }
}
