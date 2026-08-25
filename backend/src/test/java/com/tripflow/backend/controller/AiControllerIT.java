package com.tripflow.backend.controller;



import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.SocketTimeoutException;
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
import org.springframework.http.HttpStatus;
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
import com.tripflow.backend.repository.TripRepository;
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
	@Autowired private TripRepository tripRepository;
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
	void suggestItinerary_geminiReturns429_propagates429NotOur502() throws Exception {
		// SCRUM-494: a Gemini-side quota 429 must surface as 429, not fall through to the
		// generic GeminiClientException -> 502 mapping every other Gemini failure gets.
		User owner = createTestUser("geminiquota");
		Long tripId = createTrip(owner);

		geminiMockServer.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
						.body("{\"error\":\"quota exceeded\"}")
						.contentType(MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/trips/" + tripId + "/ai-suggest").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}")
						.with(asUser(owner)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.status").value(429));
	}

	@Test
	void suggestItinerary_tooManyInterests_returns400WithoutCallingGemini() throws Exception {
		User owner = createTestUser("toomanyinterests");
		Long tripId = createTrip(owner);

		String tooManyInterests = "[" + "\"a\",".repeat(10) + "\"b\"]"; // 11 elements
		mockMvc.perform(post("/api/trips/" + tripId + "/ai-suggest").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"interests\":" + tooManyInterests + "}")
						.with(asUser(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItem("interests")));

		geminiMockServer.verify(); // zero expectations registered — passes only if zero calls made
	}

	@Test
	void suggestItinerary_interestTooLong_returns400WithoutCallingGemini() throws Exception {
		User owner = createTestUser("interesttoolong");
		Long tripId = createTrip(owner);

		String longInterest = "x".repeat(51);
		mockMvc.perform(post("/api/trips/" + tripId + "/ai-suggest").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"interests\":[\"" + longInterest + "\"]}")
						.with(asUser(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItem("interests[0]")));

		geminiMockServer.verify();
	}

	@Test
	void suggestItinerary_budgetTooLong_returns400WithoutCallingGemini() throws Exception {
		User owner = createTestUser("budgettoolong");
		Long tripId = createTrip(owner);

		String longBudget = "x".repeat(51);
		mockMvc.perform(post("/api/trips/" + tripId + "/ai-suggest").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"budget\":\"" + longBudget + "\"}")
						.with(asUser(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItem("budget")));

		geminiMockServer.verify();
	}

	@Test
	void suggestItinerary_exceedsRateLimit_returns429WithoutCallingGemini() throws Exception {
		User owner = createTestUser("ratelimited");
		Long tripId = createTrip(owner);

		String geminiBody = """
				{
				  "candidates": [
				    { "content": { "role": "model", "parts": [ { "text": "{\\"summary\\":\\"ok\\",\\"stops\\":[]}" } ] }, "finishReason": "STOP" }
				  ]
				}
				""";
		// app.ratelimit.ai-suggest.capacity=5 in application-test.properties.
		for (int i = 0; i < 5; i++) {
			geminiMockServer.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
					.andRespond(withSuccess(geminiBody, MediaType.APPLICATION_JSON));
		}

		for (int i = 0; i < 5; i++) {
			mockMvc.perform(post("/api/trips/" + tripId + "/ai-suggest").with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{}")
							.with(asUser(owner)))
					.andExpect(status().isOk());
		}

		mockMvc.perform(post("/api/trips/" + tripId + "/ai-suggest").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}")
						.with(asUser(owner)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(header().exists("Retry-After"));

		// Only 5 expectations were registered — a 6th call reaching Gemini fails verify().
		geminiMockServer.verify();
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

	@Test
	void suggestItinerary_geminiReturnsEmptyCandidates_propagates502() throws Exception {
		User owner = createTestUser("emptyresp");
		Long tripId = createTrip(owner);

		// GeminiClient treats a null/empty candidate list as a failure and throws
		// GeminiClientException, which GlobalExceptionHandler maps to 502.
		String geminiBody = """
				{ "candidates": [] }
				""";
		geminiMockServer.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(geminiBody, MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/trips/" + tripId + "/ai-suggest").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}")
						.with(asUser(owner)))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.status").value(502));

		geminiMockServer.verify();
	}

	@Test
	void suggestItinerary_geminiReadTimeout_propagates502() throws Exception {
		User owner = createTestUser("timeout");
		Long tripId = createTrip(owner);

		// Mirrors the unit-level GeminiClientTest.generateContent_timeout case, but here the
		// thrown SocketTimeoutException propagates through the real GeminiClient (wrapped as
		// GeminiClientException) and out through GlobalExceptionHandler, proving the 502
		// mapping fires end-to-end rather than surfacing as a raw 500.
		geminiMockServer.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
				.andRespond(request -> { throw new SocketTimeoutException("Read timed out"); });

		mockMvc.perform(post("/api/trips/" + tripId + "/ai-suggest").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}")
						.with(asUser(owner)))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.status").value(502));

		geminiMockServer.verify();
	}

	@Test
	void suggestItinerary_promptTooLarge_returns400WithoutCallingGemini() throws Exception {
		User owner = createTestUser("toolongprompt");

		// ItineraryPromptTemplate caps the rendered prompt at 8000 chars. Stop names are
		// not @Size-bounded on count, so many long-named stops push the {{destinations}}
		// substitution past the limit. PromptTooLargeException is thrown by render()
		// before geminiClient is ever called, and GlobalExceptionHandler maps it to 400.
		List<CreateStopRequest> stops = new java.util.ArrayList<>();
		String longName = "x".repeat(200); // at the CreateStopRequest @Size(max=200) ceiling
		for (int i = 0; i < 45; i++) { // 45 * 200 = 9000 chars of names alone > 8000 limit
			stops.add(new CreateStopRequest(longName, 45.0, -75.0, null, null, null));
		}
		CreateTripRequest tripRequest = new CreateTripRequest("Big Trip", null, null, TripVisibility.PRIVATE, stops);

		MvcResult result = mockMvc.perform(post("/api/trips").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(tripRequest))
						.with(asUser(owner)))
				.andExpect(status().isCreated())
				.andReturn();
		Long tripId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(post("/api/trips/" + tripId + "/ai-suggest").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}")
						.with(asUser(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));

		// No expectations were registered — passes only if Gemini was never called.
		geminiMockServer.verify();
	}

	@Test
	void generateTrip_happyPath_returns201WithPersistedTrip() throws Exception {
		User owner = createTestUser("generate-happy");

		String geminiBody = """
				{
				  "candidates": [
				    { "content": { "role": "model", "parts": [ { "text": "{\\"title\\":\\"Kyoto Food Tour\\",\\"summary\\":\\"A foodie trip\\",\\"stops\\":[{\\"order\\":0,\\"name\\":\\"Nishiki Market\\",\\"latitude\\":35.0051,\\"longitude\\":135.7649,\\"reason\\":\\"Great street food\\"}]}" } ] }, "finishReason": "STOP" }
				  ]
				}
				""";
		geminiMockServer.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(geminiBody, MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/trips/ai-generate").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prompt\":\"3 days in Kyoto, food and temples\"}")
						.with(asUser(owner)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Kyoto Food Tour"))
				.andExpect(jsonPath("$.visibility").value("PRIVATE"))
				.andExpect(jsonPath("$.stops[0].name").value("Nishiki Market"))
				.andExpect(jsonPath("$.stops[0].notes").value("Great street food"));

		geminiMockServer.verify();
	}

	@Test
	void generateTrip_titleOverride_usesRequestTitleNotGeminis() throws Exception {
		User owner = createTestUser("generate-title");

		String geminiBody = """
				{
				  "candidates": [
				    { "content": { "role": "model", "parts": [ { "text": "{\\"title\\":\\"Gemini's Title\\",\\"summary\\":\\"x\\",\\"stops\\":[{\\"order\\":0,\\"name\\":\\"Stop\\",\\"latitude\\":1.0,\\"longitude\\":1.0,\\"reason\\":\\"r\\"}]}" } ] }, "finishReason": "STOP" }
				  ]
				}
				""";
		geminiMockServer.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(geminiBody, MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/trips/ai-generate").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prompt\":\"a trip\",\"title\":\"My Custom Title\"}")
						.with(asUser(owner)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("My Custom Title"));
	}

	@Test
	void generateTrip_geminiReturnsEmptyStops_returns422AndPersistsNothing() throws Exception {
		User owner = createTestUser("generate-empty");
		long tripsBefore = tripRepository.count();

		String geminiBody = """
				{
				  "candidates": [
				    { "content": { "role": "model", "parts": [ { "text": "{\\"title\\":\\"Empty\\",\\"summary\\":\\"x\\",\\"stops\\":[]}" } ] }, "finishReason": "STOP" }
				  ]
				}
				""";
		geminiMockServer.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(geminiBody, MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/trips/ai-generate").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prompt\":\"a trip with no stops\"}")
						.with(asUser(owner)))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422));

		// InsufficientStopsException is thrown before TripService.createTrip is ever
		// called, so nothing should have been persisted.
		org.junit.jupiter.api.Assertions.assertEquals(tripsBefore, tripRepository.count());
	}

	@Test
	void generateTrip_blankPrompt_returns400WithoutCallingGemini() throws Exception {
		User owner = createTestUser("generate-blank");

		mockMvc.perform(post("/api/trips/ai-generate").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prompt\":\"\"}")
						.with(asUser(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItem("prompt")));

		// No expectations were registered — passes only if Gemini was never called.
		geminiMockServer.verify();
	}

	@Test
	void generateTrip_geminiReturns5xx_propagates502() throws Exception {
		User owner = createTestUser("generate-geminidown");

		geminiMockServer.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST)).andRespond(withServerError());

		mockMvc.perform(post("/api/trips/ai-generate").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prompt\":\"a trip\"}")
						.with(asUser(owner)))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.status").value(502));
	}

	@Test
	void generateTrip_exceedsRateLimit_returns429WithoutCallingGemini() throws Exception {
		User owner = createTestUser("generate-ratelimited");

		String geminiBody = """
				{
				  "candidates": [
				    { "content": { "role": "model", "parts": [ { "text": "{\\"title\\":\\"T\\",\\"summary\\":\\"x\\",\\"stops\\":[{\\"order\\":0,\\"name\\":\\"S\\",\\"latitude\\":1.0,\\"longitude\\":1.0,\\"reason\\":\\"r\\"}]}" } ] }, "finishReason": "STOP" }
				  ]
				}
				""";
		// app.ratelimit.ai-generate.capacity=5 in application-test.properties.
		for (int i = 0; i < 5; i++) {
			geminiMockServer.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
					.andRespond(withSuccess(geminiBody, MediaType.APPLICATION_JSON));
		}

		for (int i = 0; i < 5; i++) {
			mockMvc.perform(post("/api/trips/ai-generate").with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"prompt\":\"a trip\"}")
							.with(asUser(owner)))
					.andExpect(status().isCreated());
		}

		mockMvc.perform(post("/api/trips/ai-generate").with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prompt\":\"a trip\"}")
						.with(asUser(owner)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(header().exists("Retry-After"));

		// Only 5 expectations were registered — a 6th call reaching Gemini fails verify().
		geminiMockServer.verify();
	}
}
