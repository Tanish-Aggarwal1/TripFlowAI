package com.tripflow.backend.client.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.tripflow.backend.exception.GeminiClientException;

class GeminiClientTest {

	private static final String BASE_URL = "https://gemini.test";
	private static final String MODEL = "gemini-test-model";
	private static final String ENDPOINT = BASE_URL + "/v1beta/models/" + MODEL + ":generateContent";

	private MockRestServiceServer server;
	private GeminiClient geminiClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl(BASE_URL)
				.defaultHeader("x-goog-api-key", "test-key")
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
		server = MockRestServiceServer.bindTo(builder).build();

		GeminiProperties props = new GeminiProperties(
				BASE_URL, "test-key", MODEL, Duration.ofSeconds(5), Duration.ofSeconds(20));
		geminiClient = new GeminiClient(builder.build(), props);
	}

	@Test
	void generateContent_success_extractsFirstCandidateText() {
		String responseBody = """
				{
				  "candidates": [
				    {
				      "content": {
				        "role": "model",
				        "parts": [ { "text": "{\\"summary\\":\\"ok\\",\\"stops\\":[]}" } ]
				      },
				      "finishReason": "STOP"
				    }
				  ]
				}
				""";

		server.expect(requestTo(ENDPOINT))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

		GeminiGenerateContentResponse response = geminiClient.generateContent("plan a trip");

		assertThat(response.firstCandidateText()).contains("\"summary\":\"ok\"");
		server.verify();
	}

	@Test
	void generateContent_4xx_wrapsInGeminiClientException() {
		server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST)).andRespond(withBadRequest());

		assertThatThrownBy(() -> geminiClient.generateContent("plan a trip"))
				.isInstanceOf(GeminiClientException.class)
				.hasMessageContaining("Gemini request failed");
	}

	@Test
	void generateContent_5xx_wrapsInGeminiClientException() {
		server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST)).andRespond(withServerError());

		assertThatThrownBy(() -> geminiClient.generateContent("plan a trip"))
				.isInstanceOf(GeminiClientException.class)
				.hasMessageContaining("Gemini request failed");
	}

	@Test
	void generateContent_timeout_wrapsInGeminiClientException() {
		server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
				.andRespond(request -> { throw new SocketTimeoutException("Read timed out"); });

		assertThatThrownBy(() -> geminiClient.generateContent("plan a trip"))
				.isInstanceOf(GeminiClientException.class)
				.hasMessageContaining("Gemini request failed");
	}

	@Test
	void generateContent_emptyCandidates_throwsGeminiClientException() {
		server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> geminiClient.generateContent("plan a trip"))
				.isInstanceOf(GeminiClientException.class)
				.hasMessageContaining("empty response");
	}
}