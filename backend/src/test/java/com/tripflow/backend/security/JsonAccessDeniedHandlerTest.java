package com.tripflow.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class JsonAccessDeniedHandlerTest {
	private final ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	private final JsonAccessDeniedHandler handler = new JsonAccessDeniedHandler(objectMapper);
	
	@Test
	void handle_writes403ApiErrorJson() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/api/admin/reports");
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.handle(request, response, new AccessDeniedException("denied"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");

		JsonNode body = objectMapper.readTree(response.getContentAsString());
		assertThat(body.get("status").asInt()).isEqualTo(403);
		assertThat(body.get("error").asText()).isEqualTo("Forbidden");
		assertThat(body.get("message").asText()).isEqualTo("You do not have access to this resource");
		assertThat(body.get("path").asText()).isEqualTo("/api/admin/reports");
	}
}
