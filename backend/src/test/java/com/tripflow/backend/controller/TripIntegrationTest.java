package com.tripflow.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.beans.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
public class TripIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void createTrip_andRetrieveIt_persistsAndReloadsCorrectly() throws Exception {

		CreateStopRequest stop = new CreateStopRequest();
		stop.setName("Cottage");
		stop.setLatitude(45.0);
		stop.setLongitude(-79.9);
		stop.setAddress("Muskoka");

		CreateTripRequest tripRequest = new CreateTripRequest();
		tripRequest.setTitle("Weekend Getaway");
		tripRequest.setDescription("Cottage trip");
		tripRequest.setTags(List.of("cottage", "ontario"));
		tripRequest.setVisibility(TripVisibility.PRIVATE);
		tripRequest.setStops(List.of(stop));

		MvcResult createResult = mockMvc
				.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(tripRequest)).with(user("1")))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Weekend Getaway"))
				.andExpect(jsonPath("$.stops[0].name").value("Cottage")).andReturn();

		String responseBody = createResult.getResponse().getContentAsString();
		Long tripId = objectMapper.readTree(responseBody).get("id").asLong();

		mockMvc.perform(get("/api/trips/" + tripId).with(csrf()).with(user("1"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Weekend Getaway")).andExpect(jsonPath("$.stops").isArray())
				.andExpect(jsonPath("$.stops[0].stopOrder").value(0))
				.andExpect(jsonPath("$.stops[0].name").value("Cottage"));
	}

	@Test
	void getTrip_privateTripAsNonOwner_returns403() throws Exception {

		CreateStopRequest stop = new CreateStopRequest();
		stop.setName("Cottage");
		stop.setLatitude(45.0);
		stop.setLongitude(-79.9);

		CreateTripRequest tripRequest = new CreateTripRequest();
		tripRequest.setTitle("Private Trip");
		tripRequest.setVisibility(TripVisibility.PRIVATE);
		tripRequest.setStops(List.of(stop));

		MvcResult createResult = mockMvc
				.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(tripRequest)).with(user("1")))
				.andExpect(status().isCreated()).andReturn();

		String responseBody = createResult.getResponse().getContentAsString();
		Long tripId = objectMapper.readTree(responseBody).get("id").asLong();

		mockMvc.perform(get("/api/trips/" + tripId).with(csrf()).principal(() -> "2")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403)).andExpect(jsonPath("$.error").value("Forbidden"));
	}

	@Test
	void createTrip_invalidRequest_returns400WithValidationErrors() throws Exception {

		CreateTripRequest tripRequest = new CreateTripRequest();
		tripRequest.setTitle("");
		tripRequest.setVisibility(TripVisibility.PRIVATE);
		tripRequest.setStops(List.of());

		mockMvc.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(tripRequest)).with(user("1")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.fieldErrors").isArray());
	}
}
