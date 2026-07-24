package com.tripflow.backend.controller;



import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

/**
 * Mirrors RouteOptimizationControllerIT's pattern: real Spring context, real
 * Postgres via Testcontainers, real security filter chain — only the
 * geminiRestClient bean is swapped for one bound to a MockRestServiceServer.
 * No real network call reaches Gemini.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class AiControllerIT {
	
	private static final String GEMINI_BASE_URL = "https://gemini.test";
	private static final String MODEL = "gemini-test-model";
	private static final String ENDPOINT = GEMINI_BASE_URL + "/v1beta/models/" + MODEL + ":generateContent";

	@TestConfiguration
	static class GeminiMockConfig {

		@Bean
		RestClient.Builder geminiMockRestClientBuilder() {
			return RestClient.builder().baseUrl(GEMINI_BASE_URL);
		}

		@Bean
		MockRestServiceServer geminiMockServer() {
			return MockRestServiceServer.bindTo(geminiMockRestClientBuilder()).build();
		}

		@Bean
		@Primary
		RestClient mockGeminiRestClient() {
			geminiMockServer(); // bind-before-build ordering
			return geminiMockRestClientBuilder().build();
		}
	}

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private MockRestServiceServer geminiMockServer;

	@BeforeEach
	void resetMockServer() {
		geminiMockServer.reset();
	}

	private final ObjectMapper objectMapper = new ObjectMapper();

	private User createTestUser(String suffix) {
		User user = new User();
		user.setUsername("ai-" + suffix);
		user.setEmail("ai-" + suffix + "@tripflow.com");
		user.setPasswordHash("hashedpassword123");
		return userRepository.save(user);
	}

	private RequestPostProcessor asUser(User user) {
		UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail());
		var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		return authentication(auth);
	}

	private Long createTrip(User owner) throws Exception {
		CreateStopRequest stop = new CreateStopRequest("Ottawa", 45.4215, -75.6972, null, null, null);
		CreateTripRequest tripRequest = new CreateTripRequest("Test Trip", null, null, TripVisibility.PRIVATE, List.of(stop));

		MvcResult result = mockMvc.perform(post("/api/trips").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(tripRequest))
						.with(asUser(owner)))
				.andExpect(status().isCreated())
				.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	@Test
	void suggestItinerary_happyPath_returns200WithParsedSuggestion() throws Exception {
		User owner = createTestUser("owner");
		Long tripId = createTrip(owner);

		String geminiBody = """
				{
				  "candidates": [
				    { "content": { "role": "model", "parts": [ { "text": "{\\"summary\\":\\"Nice trip\\",\\"stops\\":[{\\"order\\":0,\\"name\\":\\"Byward Market\\",\\"latitude\\":45.4285,\\"longitude\\":-75.6935,\\"reason\\":\\"Close to your existing stop\\"}]}" } ] }, "finishReason": "STOP" }
				  ]
				}
				""";
		geminiMockServer.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(geminiBody, MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/trips/" + tripId + "/ai-suggest").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"interests\":[\"food\"],\"budget\":\"moderate\",\"pace\":\"slow\"}")
						.with(asUser(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tripId").value(tripId))
				.andExpect(jsonPath("$.summary").value("Nice trip"))
				.andExpect(jsonPath("$.stops[0].name").value("Byward Market"));

		geminiMockServer.verify();
	}

	@Test
	void suggestItinerary_nonOwner_returns403_withoutCallingGemini() throws Exception {
		User owner = createTestUser("owner2");
		User other = createTestUser("other2");
		Long tripId = createTrip(owner);

		mockMvc.perform(post("/api/trips/" + tripId + "/ai-suggest").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}")
						.with(asUser(other)))
				.andExpect(status().isForbidden());

		geminiMockServer.verify(); // zero expectations registered — passes only if zero calls made
	}

	@Test
	void suggestItinerary_nonExistentTrip_returns404() throws Exception {
		User user = createTestUser("notfound");

		mockMvc.perform(post("/api/trips/999999/ai-suggest").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}")
						.with(asUser(user)))
				.andExpect(status().isNotFound());
	}

	@Test
	void suggestItinerary_geminiReturns5xx_propagates502() throws Exception {
		User owner = createTestUser("geminidown");
		Long tripId = createTrip(owner);

		geminiMockServer.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST)).andRespond(withServerError());

		mockMvc.perform(post("/api/trips/" + tripId + "/ai-suggest").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}")
						.with(asUser(owner)))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.status").value(502));
	}

	@Test
	void suggestItinerary_geminiReturnsNonJson_propagates502() throws Exception {
		User owner = createTestUser("badjson");
		Long tripId = createTrip(owner);

		String geminiBody = """
				{ "candidates": [ { "content": { "role": "model", "parts": [ { "text": "not json at all" } ] }, "finishReason": "STOP" } ] }
				""";
		geminiMockServer.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(geminiBody, MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/trips/" + tripId + "/ai-suggest").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}")
						.with(asUser(owner)))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.status").value(502));
	}
}
