package com.tripflow.backend.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {

		String header = request.getHeader("Authorization");

		if (header != null && header.startsWith("Bearer ")) {
			String token = header.substring(7);

			try {
				if (jwtService.isValid(token)) {
					Long userId = jwtService.extractUserId(token);
					String email = jwtService.extractEmail(token);
					UserPrincipal principal = new UserPrincipal(userId, email);
					var authToken = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
					SecurityContextHolder.getContext().setAuthentication(authToken);
					log.debug("JWT authenticated userId={} on {}", userId, request.getRequestURI());
				} else {
					log.warn("JWT rejected as invalid on {}", request.getRequestURI());
				}
			} catch (Exception ex) {
				// Never log the token value itself.
				log.warn("JWT validation threw on {}: {}", request.getRequestURI(), ex.getMessage());
			}
		}

		filterChain.doFilter(request, response);
	}
}