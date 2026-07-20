package com.tripflow.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class JsonAuthenticationEntryPointTest {

	private final ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	private final JsonAuthenticationEntryPoint entryPoint = new JsonAuthenticationEntryPoint(objectMapper);

	@Test
	void commence_writes401ApiErrorJson() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/api/trips");
		MockHttpServletResponse response = new MockHttpServletResponse();

		entryPoint.commence(request, response, new BadCredentialsException("no auth"));

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");

		JsonNode body = objectMapper.readTree(response.getContentAsString());
		assertThat(body.get("status").asInt()).isEqualTo(401);
		assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
		assertThat(body.get("message").asText()).isEqualTo("Authentication required");
		assertThat(body.get("path").asText()).isEqualTo("/api/trips");
		assertThat(body.get("timestamp").isTextual()).isTrue();
	}
}
