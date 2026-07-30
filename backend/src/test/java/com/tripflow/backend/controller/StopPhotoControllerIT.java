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
import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopPhotoRequest;
import com.tripflow.backend.repository.PlaceRepository;
import com.tripflow.backend.repository.StopRepository;
import com.tripflow.backend.repository.TripRepository;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class StopPhotoControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private StopRepository stopRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User createUser(String suffix) {
        User user = new User();
        user.setUsername("photouser-" + suffix);
        user.setEmail("photouser-" + suffix + "@example.com");
        user.setPasswordHash("hashed");
        return userRepository.save(user);
    }

    private RequestPostProcessor asUser(User user) {
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return authentication(auth);
    }

    private Long createStop(User owner, TripVisibility visibility) {
        Trip trip = new Trip();
        trip.setUser(owner);
        trip.setTitle("Photo Trip");
        trip.setVisibility(visibility);
        Trip savedTrip = tripRepository.save(trip);

        Place place = new Place();
        place.setName("Photo Spot");
        place.setLatitude(45.0);
        place.setLongitude(-79.0);
        Place savedPlace = placeRepository.save(place);

        Stop stop = new Stop();
        stop.setTrip(savedTrip);
        stop.setPlace(savedPlace);
        stop.setStopOrder(0);
        return stopRepository.save(stop).getId();
    }

    @Test
    void uploadFlow_owner_signatureThenPersistThenList() throws Exception {
        User owner = createUser("happy");
        Long stopId = createStop(owner, TripVisibility.PRIVATE);

        // 1. signature
        mockMvc.perform(post("/api/stops/" + stopId + "/photo-signature").with(csrf()).with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature").exists())
                .andExpect(jsonPath("$.apiKey").exists())
                .andExpect(jsonPath("$..apiSecret").doesNotExist());

        // 2. mock upload already happened client-side; persist the result
        CreateStopPhotoRequest request = new CreateStopPhotoRequest(
                "https://res.cloudinary.com/demo/image/upload/v1/mock.jpg", "mock-public-id", "A view");

        MvcResult createResult = mockMvc.perform(post("/api/stops/" + stopId + "/photos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)).with(asUser(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(request.url()))
                .andReturn();

        Long photoId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // 3. list
        mockMvc.perform(get("/api/stops/" + stopId + "/photos").with(asUser(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(photoId))
                .andExpect(jsonPath("$[0].caption").value("A view"));
    }

    @Test
    void signature_nonOwner_returns403() throws Exception {
        User owner = createUser("sigowner");
        User other = createUser("sigother");
        Long stopId = createStop(owner, TripVisibility.PRIVATE);

        mockMvc.perform(post("/api/stops/" + stopId + "/photo-signature").with(csrf()).with(asUser(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void addPhoto_missingStop_returns404() throws Exception {
        User owner = createUser("missingowner");
        CreateStopPhotoRequest request = new CreateStopPhotoRequest(
                "https://res.cloudinary.com/demo/image/upload/v1/x.jpg", null, null);

        mockMvc.perform(post("/api/stops/999999/photos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)).with(asUser(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listPhotos_nonOwnerOnPublicTrip_returns200() throws Exception {
        User owner = createUser("pubowner");
        User other = createUser("pubother");
        Long stopId = createStop(owner, TripVisibility.PUBLIC);

        mockMvc.perform(get("/api/stops/" + stopId + "/photos").with(asUser(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listPhotos_nonOwnerOnPrivateTrip_returns403() throws Exception {
        User owner = createUser("privowner");
        User other = createUser("privother");
        Long stopId = createStop(owner, TripVisibility.PRIVATE);

        mockMvc.perform(get("/api/stops/" + stopId + "/photos").with(asUser(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletePhoto_nonOwner_returns403() throws Exception {
        User owner = createUser("delowner");
        User other = createUser("delother");
        Long stopId = createStop(owner, TripVisibility.PRIVATE);

        CreateStopPhotoRequest request = new CreateStopPhotoRequest(
                "https://res.cloudinary.com/demo/image/upload/v1/del.jpg", null, null);
        MvcResult createResult = mockMvc.perform(post("/api/stops/" + stopId + "/photos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)).with(asUser(owner)))
                .andExpect(status().isCreated()).andReturn();
        Long photoId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/stops/" + stopId + "/photos/" + photoId).with(csrf()).with(asUser(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletePhoto_owner_returns204() throws Exception {
        User owner = createUser("okdelowner");
        Long stopId = createStop(owner, TripVisibility.PRIVATE);

        CreateStopPhotoRequest request = new CreateStopPhotoRequest(
                "https://res.cloudinary.com/demo/image/upload/v1/okdel.jpg", null, null);
        MvcResult createResult = mockMvc.perform(post("/api/stops/" + stopId + "/photos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)).with(asUser(owner)))
                .andExpect(status().isCreated()).andReturn();
        Long photoId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/stops/" + stopId + "/photos/" + photoId).with(csrf()).with(asUser(owner)))
                .andExpect(status().isNoContent());
    }
}