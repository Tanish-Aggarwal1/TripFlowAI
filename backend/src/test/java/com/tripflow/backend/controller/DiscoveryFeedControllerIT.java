package com.tripflow.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User createTestUser(String suffix) {
        User user = new User();
        user.setUsername("feed-" + suffix);
        user.setEmail("feed-" + suffix + "@example.com");
        user.setPasswordHash("hashed");
        return userRepository.save(user);
    }

    // SOCIAL-06/D-06: interests live on the viewer's stored profile row, never inferred from
    // trip history — this helper is the only place ranking tests seed that source.
    private User createTestUserWithInterests(String suffix, List<String> interests) {
        User user = createTestUser(suffix);
        user.setInterests(interests);
        return userRepository.save(user);
    }

    private RequestPostProcessor asUser(User user) {
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(auth);
    }

    private Long createTrip(User owner, String title, String description, TripVisibility visibility,
            List<String> tags) throws Exception {
        CreateTripRequest request = new CreateTripRequest(
                title,
                description,
                tags,
                visibility,
                List.of(new CreateStopRequest("Byward Market", 45.4285, -75.6935, "55 ByWard Market Sq", null,
                        "Try the beavertails")));

        String body = mockMvc.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)).with(asUser(owner)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    // SOCIAL-06 Task 1: pins the ORDER BY's created_at tiebreaker deliberately, following the
    // established TripSearchRepositoryIT#searchOwnedTrips_equalCreatedAt_ordersByIdDescendingStably
    // pattern of writing an exact createdAt value straight to the row after the entity is
    // persisted (BaseEntity.createdAt is @CreatedDate/updatable=false, so it can't be set through
    // JPA — a raw UPDATE is the only way to control it deterministically in a test).
    private void setCreatedAt(Long tripId, Instant instant) {
        jdbcTemplate.update("UPDATE trips SET created_at = ? WHERE id = ?", Timestamp.from(instant), tripId);
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

    // ---------- SOCIAL-06 (D-05/D-06): interest-based ranking ----------

    @Test
    void getFeed_olderMatchingTripOutranksNewerNonMatchingTrip() throws Exception {
        User owner = createTestUser("owner6");
        User viewer = createTestUserWithInterests("viewer6", List.of("hiking"));

        Long matchingId = createTrip(owner, "Older Matching Trip", null, TripVisibility.PUBLIC, List.of("hiking"));
        Long nonMatchingId = createTrip(owner, "Newer Non-Matching Trip", null, TripVisibility.PUBLIC,
                List.of("beach"));
        setCreatedAt(matchingId, Instant.parse("2026-01-01T00:00:00Z"));
        setCreatedAt(nonMatchingId, Instant.parse("2026-06-01T00:00:00Z"));

        mockMvc.perform(get("/api/discovery/feed").with(asUser(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(matchingId))
                .andExpect(jsonPath("$.content[1].id").value(nonMatchingId));
    }

    @Test
    void getFeed_bothMatchingTrips_moreRecentAppearsFirst() throws Exception {
        User owner = createTestUser("owner7");
        User viewer = createTestUserWithInterests("viewer7", List.of("hiking"));

        Long olderMatch = createTrip(owner, "Older Match", null, TripVisibility.PUBLIC, List.of("hiking"));
        Long newerMatch = createTrip(owner, "Newer Match", null, TripVisibility.PUBLIC, List.of("hiking"));
        setCreatedAt(olderMatch, Instant.parse("2026-01-01T00:00:00Z"));
        setCreatedAt(newerMatch, Instant.parse("2026-06-01T00:00:00Z"));

        mockMvc.perform(get("/api/discovery/feed").with(asUser(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(newerMatch))
                .andExpect(jsonPath("$.content[1].id").value(olderMatch));
    }

    @Test
    void getFeed_neitherTripMatches_moreRecentAppearsFirst() throws Exception {
        User owner = createTestUser("owner8");
        User viewer = createTestUserWithInterests("viewer8", List.of("hiking"));

        Long olderNonMatch = createTrip(owner, "Older Non-Match", null, TripVisibility.PUBLIC, List.of("beach"));
        Long newerNonMatch = createTrip(owner, "Newer Non-Match", null, TripVisibility.PUBLIC, List.of("city"));
        setCreatedAt(olderNonMatch, Instant.parse("2026-01-01T00:00:00Z"));
        setCreatedAt(newerNonMatch, Instant.parse("2026-06-01T00:00:00Z"));

        mockMvc.perform(get("/api/discovery/feed").with(asUser(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(newerNonMatch))
                .andExpect(jsonPath("$.content[1].id").value(olderNonMatch));
    }

    @Test
    void getFeed_viewerWithEmptyInterests_isPureRecencyOrderWithNoError() throws Exception {
        User owner = createTestUser("owner9");
        User viewer = createTestUserWithInterests("viewer9", List.of());

        Long older = createTrip(owner, "Older Trip", null, TripVisibility.PUBLIC, List.of("hiking"));
        Long newer = createTrip(owner, "Newer Trip", null, TripVisibility.PUBLIC, List.of("beach"));
        setCreatedAt(older, Instant.parse("2026-01-01T00:00:00Z"));
        setCreatedAt(newer, Instant.parse("2026-06-01T00:00:00Z"));

        mockMvc.perform(get("/api/discovery/feed").with(asUser(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(newer))
                .andExpect(jsonPath("$.content[1].id").value(older));
    }

    @Test
    void getFeed_privateTripWithMatchingTag_neverAppears() throws Exception {
        User owner = createTestUser("owner10");
        User viewer = createTestUserWithInterests("viewer10", List.of("hiking"));

        createTrip(owner, "Private Matching Trip", null, TripVisibility.PRIVATE, List.of("hiking"));
        Long publicId = createTrip(owner, "Public Non-Matching Trip", null, TripVisibility.PUBLIC, List.of("beach"));

        mockMvc.perform(get("/api/discovery/feed").with(asUser(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(publicId));
    }

    @Test
    void getFeed_twoViewersWithDifferentInterests_receiveDifferentOrderings() throws Exception {
        User owner = createTestUser("owner11");
        User hikingViewer = createTestUserWithInterests("hikingviewer11", List.of("hiking"));
        User beachViewer = createTestUserWithInterests("beachviewer11", List.of("beach"));

        Long hikingTrip = createTrip(owner, "Hiking Trip", null, TripVisibility.PUBLIC, List.of("hiking"));
        Long beachTrip = createTrip(owner, "Beach Trip", null, TripVisibility.PUBLIC, List.of("beach"));
        setCreatedAt(hikingTrip, Instant.parse("2026-01-01T00:00:00Z"));
        setCreatedAt(beachTrip, Instant.parse("2026-06-01T00:00:00Z"));

        mockMvc.perform(get("/api/discovery/feed").with(asUser(hikingViewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(hikingTrip))
                .andExpect(jsonPath("$.content[1].id").value(beachTrip));

        mockMvc.perform(get("/api/discovery/feed").with(asUser(beachViewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(beachTrip))
                .andExpect(jsonPath("$.content[1].id").value(hikingTrip));
    }

    @Test
    void getFeed_rankingUsesStoredInterests_notViewersOwnTripTags() throws Exception {
        // D-06: the viewer's own trip's tag ("food") must never act as a stand-in interest
        // signal. The viewer's stored interests are empty, so this must fall back to pure
        // recency — if ranking wrongly inferred interests from the viewer's own trips, the
        // older "food"-tagged stranger trip would incorrectly outrank the newer one.
        User viewer = createTestUserWithInterests("viewer12", List.of());
        createTrip(viewer, "Viewer's Own Food Trip", null, TripVisibility.PUBLIC, List.of("food"));

        User stranger = createTestUser("owner12");
        Long olderFoodTrip = createTrip(stranger, "Older Food Trip", null, TripVisibility.PUBLIC, List.of("food"));
        Long newerOtherTrip = createTrip(stranger, "Newer Other Trip", null, TripVisibility.PUBLIC,
                List.of("other"));
        setCreatedAt(olderFoodTrip, Instant.parse("2026-01-01T00:00:00Z"));
        setCreatedAt(newerOtherTrip, Instant.parse("2026-06-01T00:00:00Z"));

        String body = mockMvc.perform(get("/api/discovery/feed").with(asUser(viewer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).get("content");

        // Recency order across all 3 PUBLIC trips (viewer's own trip has no explicit createdAt
        // override, so it sits at its natural insertion time — before either stranger trip in
        // this sequence). The load-bearing assertion is that newerOtherTrip precedes
        // olderFoodTrip, not any particular position for the viewer's own trip.
        List<Long> ids = new java.util.ArrayList<>();
        content.forEach(node -> ids.add(node.get("id").asLong()));
        assertThat(ids.indexOf(newerOtherTrip)).isLessThan(ids.indexOf(olderFoodTrip));
    }
}
