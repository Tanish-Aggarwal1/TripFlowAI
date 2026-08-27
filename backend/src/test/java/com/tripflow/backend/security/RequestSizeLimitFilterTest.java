package com.tripflow.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class RequestSizeLimitFilterTest {

	private static final long MAX_BYTES = 100;

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
	private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(MAX_BYTES, objectMapper);

	@Mock private FilterChain filterChain;

	@Test
	void contentLengthOverLimit_rejectedWith413AndChainNeverInvoked() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/api/trips");
		request.setContent(new byte[(int) MAX_BYTES + 1]);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request, response, filterChain);

		assertThat(response.getStatus()).isEqualTo(413);
		JsonNode body = objectMapper.readTree(response.getContentAsString());
		assertThat(body.get("status").asInt()).isEqualTo(413);
		assertThat(body.get("path").asText()).isEqualTo("/api/trips");
		verify(filterChain, never()).doFilter(any(), any());
	}

	@Test
	void contentLengthWithinLimit_chainInvokedWithWrappedRequest() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setContent(new byte[(int) MAX_BYTES]);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request, response, filterChain);

		verify(filterChain).doFilter(any(HttpServletRequest.class), any());
		assertThat(response.getStatus()).isEqualTo(200); // MockHttpServletResponse default, untouched
	}
}
