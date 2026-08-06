package com.tripflow.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * End-to-end IT for GET /api/trips/{id}/calendar.ics (SCRUM-176). No external API
 * involved, so this hits the real IcsExportService/TripService stack against real
 * Postgres — no mocking needed beyond the usual Testcontainers setup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class TripExportControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private User createTestUser(String suffix) {
		User user = new User();
		user.setUsername("ics-" + suffix);
		user.setEmail("ics-" + suffix + "@example.com");
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
				List.of(
						new CreateStopRequest("Byward Market", 45.4285, -75.6935, "55 Byward Market Sq", null, null),
						new CreateStopRequest("Parliament Hill", 45.4236, -75.7003, null, null, null)));

		MvcResult result = mockMvc
				.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)).with(asUser(owner)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	@Test
	void exportIcs_owner_returnsIcsFileWithOneVeventPerStop() throws Exception {
		User owner = createTestUser("owner");
		Long tripId = createTrip(owner, "Ottawa Weekend", TripVisibility.PRIVATE);

		MvcResult result = mockMvc.perform(get("/api/trips/" + tripId + "/calendar.ics").with(asUser(owner)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "text/calendar"))
				.andExpect(header().string("Content-Disposition",
						org.hamcrest.Matchers.containsString("attachment; filename=\"Ottawa Weekend.ics\"")))
				.andReturn();

		String ics = result.getResponse().getContentAsString();
		assertThatIcsIsValid(ics);
		org.assertj.core.api.Assertions.assertThat(ics).contains("SUMMARY:Byward Market");
		org.assertj.core.api.Assertions.assertThat(ics).contains("SUMMARY:Parliament Hill");
		org.assertj.core.api.Assertions.assertThat(ics).contains("LOCATION:55 Byward Market Sq");
	}

	@Test
	void exportIcs_privateTripNonOwner_returns404() throws Exception {
		User owner = createTestUser("owner2");
		User other = createTestUser("other2");
		Long tripId = createTrip(owner, "Private Trip", TripVisibility.PRIVATE);

		mockMvc.perform(get("/api/trips/" + tripId + "/calendar.ics").with(asUser(other)))
				.andExpect(status().isNotFound());
	}

	@Test
	void exportIcs_publicTripNonOwner_returnsIcsFile() throws Exception {
		User owner = createTestUser("owner3");
		User other = createTestUser("other3");
		Long tripId = createTrip(owner, "Public Trip", TripVisibility.PUBLIC);

		mockMvc.perform(get("/api/trips/" + tripId + "/calendar.ics").with(asUser(other)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "text/calendar"));
	}

	@Test
	void exportIcs_nonExistentTrip_returns404() throws Exception {
		User user = createTestUser("notfound");

		mockMvc.perform(get("/api/trips/999999/calendar.ics").with(asUser(user)))
				.andExpect(status().isNotFound());
	}

	@Test
	void exportIcs_titleWithUnsafeCharacters_sanitizesFilename() throws Exception {
		User owner = createTestUser("unsafe");
		Long tripId = createTrip(owner, "Trip: \"Fun\" / Times?", TripVisibility.PRIVATE);

		mockMvc.perform(get("/api/trips/" + tripId + "/calendar.ics").with(asUser(owner)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition",
						org.hamcrest.Matchers.containsString("filename=\"Trip Fun  Times.ics\"")));
	}

	@Test
	void exportIcs_titleOver100Characters_truncatesFilenameTo100() throws Exception {
		User owner = createTestUser("longtitle");
		String longTitle = "a".repeat(120); // within CreateTripRequest's 150-char title cap
		Long tripId = createTrip(owner, longTitle, TripVisibility.PRIVATE);

		mockMvc.perform(get("/api/trips/" + tripId + "/calendar.ics").with(asUser(owner)))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition",
						org.hamcrest.Matchers.containsString("filename=\"" + "a".repeat(100) + ".ics\"")));
	}

	private static void assertThatIcsIsValid(String ics) {
		org.assertj.core.api.Assertions.assertThat(ics).contains("BEGIN:VCALENDAR");
		org.assertj.core.api.Assertions.assertThat(ics).contains("VERSION:2.0");
		org.assertj.core.api.Assertions.assertThat(ics).contains("END:VCALENDAR");
	}
}
