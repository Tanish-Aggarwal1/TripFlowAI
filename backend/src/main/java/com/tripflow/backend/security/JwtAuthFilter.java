package com.tripflow.backend.security;

import java.io.IOException;
import java.util.Optional;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tripflow.backend.repository.UserRepository;

import io.jsonwebtoken.Claims;
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
	private final UserRepository userRepository;

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {

		String header = request.getHeader("Authorization");

		if (header != null && header.startsWith("Bearer ")) {
			String token = header.substring(7);

			try {
				Optional<Claims> claims = jwtService.parseIfValid(token);
				if (claims.isPresent()) {
					Long userId = Long.parseLong(claims.get().getSubject());
					Integer tokenVersion = claims.get().get("tv", Integer.class);
					Optional<Integer> currentVersion = userRepository.findTokenVersionById(userId);

					// currentVersion.empty() covers a deleted user (previously surfaced as a raw FK
					// error deeper in the call stack, e.g. TripService.createTrip); a mismatch means
					// the token predates the user's last "invalidate everything" event (M-7).
					if (currentVersion.isPresent() && currentVersion.get().equals(tokenVersion)) {
						String email = claims.get().get("email", String.class);
						UserPrincipal principal = new UserPrincipal(userId, email);
						var authToken = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
						SecurityContextHolder.getContext().setAuthentication(authToken);
						log.debug("JWT authenticated userId={} on {}", userId, request.getRequestURI());
					} else {
						log.warn("JWT rejected as revoked (token_version mismatch) userId={} on {}", userId,
								request.getRequestURI());
					}
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