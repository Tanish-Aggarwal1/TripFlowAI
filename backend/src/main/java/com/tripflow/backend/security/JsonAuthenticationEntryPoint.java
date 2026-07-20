package com.tripflow.backend.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Replaces Spring Security's default (HTML, no body) 401 response with the
 * canonical ApiError JSON shape. Fires whenever an unauthenticated request
 * hits a protected endpoint — missing, malformed, or expired JWT.
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		SecurityErrorWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED,
				"Authentication required", request.getRequestURI());
	}
}