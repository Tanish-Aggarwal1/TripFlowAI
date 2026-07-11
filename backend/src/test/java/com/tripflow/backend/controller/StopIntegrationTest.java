package com.tripflow.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.beans.User;
import com.tripflow.backend.beans.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
public class StopIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String createTestUser(String suffix) {
        User user = new User();
        user.setUsername("stoptest-" + suffix);
        user.setEmail("stoptest-" + suffix + "@example.com");
        user.setPasswordHash("hashed");
        Long id = userRepository.save(user).getId();
        return id.toString();
    }

    private Long createTripAsUser(String userId) throws Exception {
        CreateStopRequest stop = new CreateStopRequest();
        stop.setName("Cottage");
        stop.setLatitude(45.0);
        stop.setLongitude(-79.9);

        CreateTripRequest tripRequest = new CreateTripRequest();
        tripRequest.setTitle("Muskoka Trip");
        tripRequest.setVisibility(TripVisibility.PRIVATE);
        tripRequest.setStops(List.of(stop));

        MvcResult result = mockMvc.perform(post("/api/trips").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tripRequest))
                        .with(user(userId)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void addStop_owner_returns201() throws Exception {
        String ownerId = createTestUser("addowner");
        Long tripId = createTripAsUser(ownerId);

        CreateStopRequest newStop = new CreateStopRequest();
        newStop.setName("Gas Station");
        newStop.setLatitude(44.5);
        newStop.setLongitude(-79.6);

        mockMvc.perform(post("/api/trips/" + tripId + "/stops").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStop))
                        .with(user(ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stopOrder").value(1))
                .andExpect(jsonPath("$.name").value("Gas Station"));
    }

    @Test
    void addStop_nonOwner_returns403() throws Exception {
        String ownerId = createTestUser("nonownerowner");
        String otherId = createTestUser("nonownerother");
        Long tripId = createTripAsUser(ownerId);

        CreateStopRequest newStop = new CreateStopRequest();
        newStop.setName("Gas Station");
        newStop.setLatitude(44.5);
        newStop.setLongitude(-79.6);

        mockMvc.perform(post("/api/trips/" + tripId + "/stops").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStop))
                        .with(user(otherId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void listStops_owner_returnsStops() throws Exception {
        String ownerId = createTestUser("listowner");
        Long tripId = createTripAsUser(ownerId);

        mockMvc.perform(get("/api/trips/" + tripId + "/stops").with(csrf()).with(user(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cottage"));
    }

    @Test
    void deleteStop_missingStop_returns404() throws Exception {
        String ownerId = createTestUser("deleteowner");
        Long tripId = createTripAsUser(ownerId);

        mockMvc.perform(delete("/api/trips/" + tripId + "/stops/999999").with(csrf()).with(user(ownerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}