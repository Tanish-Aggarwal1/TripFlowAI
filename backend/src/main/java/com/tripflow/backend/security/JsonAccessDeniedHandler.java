package com.tripflow.backend.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Replaces Spring Security's default (HTML, no body) 403 response with the
 * canonical ApiError JSON shape. Fires when Spring Security's own authorization
 * layer rejects an authenticated request — not currently reachable in this app
 * (no .hasRole(...)/@PreAuthorize rules exist yet), but wired now so it's correct
 * the moment role-based authorization is introduced.
 */
@Component
@RequiredArgsConstructor
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		SecurityErrorWriter.write(response, objectMapper, HttpStatus.FORBIDDEN,
				"You do not have access to this resource", request.getRequestURI());
	}
}