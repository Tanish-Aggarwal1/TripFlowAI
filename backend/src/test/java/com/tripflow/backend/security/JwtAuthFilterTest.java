package com.tripflow.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

	private static final String SECRET =
			"test-jwt-secret-must-be-at-least-256-bits-long-for-hmac-sha256";

	@Mock private FilterChain filterChain;

	private JwtService jwtService;
	private JwtAuthFilter filter;

	@BeforeEach
	void setUp() {
		jwtService = new JwtService(SECRET, 3_600_000L);
		filter = new JwtAuthFilter(jwtService);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void noAuthorizationHeader_leavesContextUnauthenticated() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void invalidBearerToken_leavesContextUnauthenticated() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer invalid-token");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void validBearerToken_setsAuthenticationWithUserPrincipal() throws Exception {
		String token = jwtService.generateToken(55L, "user@example.com");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request, response, filterChain);

		var auth = SecurityContextHolder.getContext().getAuthentication();
		assertThat(auth).isNotNull();
		assertThat(auth.getPrincipal()).isInstanceOf(UserPrincipal.class);
		UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
		assertThat(principal.userId()).isEqualTo(55L);
		assertThat(principal.email()).isEqualTo("user@example.com");
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void malformedHeader_noBearerPrefix_ignored() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Basic sometoken");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void expiredToken_leavesContextUnauthenticated() throws Exception {
		// Separate JwtService instance configured with an already-elapsed expiry, used
		// only to mint a token whose exp claim is in the past. The filter still validates
		// with the normal jwtService from setUp() — JJWT's parser reads the exp claim
		// embedded in the token itself, not the verifying service's own configured expiry.
		JwtService expiredJwtService = new JwtService(SECRET, -1_000L);
		String expiredToken = expiredJwtService.generateToken(55L, "user@example.com");

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + expiredToken);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(filterChain).doFilter(request, response);
	}
}
