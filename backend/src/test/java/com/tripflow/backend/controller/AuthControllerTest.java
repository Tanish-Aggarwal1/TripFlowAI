package com.tripflow.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripflow.backend.config.RefreshTokenProperties;
import com.tripflow.backend.dto.AuthResponse;
import com.tripflow.backend.dto.LoginRequest;
import com.tripflow.backend.dto.RegisterRequest;
import com.tripflow.backend.exception.DuplicateEmailException;
import com.tripflow.backend.exception.InvalidCredentialsException;
import com.tripflow.backend.ratelimit.RateLimitProperties;
import com.tripflow.backend.ratelimit.RateLimiterService;
import com.tripflow.backend.security.JwtService;
import com.tripflow.backend.service.AuthService;
import com.tripflow.backend.service.RefreshTokenService;

import jakarta.servlet.http.Cookie;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	/** Real values rather than a mocked record — the cookie attributes are the thing under
	 * test in the refresh cases below, so stubbing them away would defeat the point. */
	@TestConfiguration
	static class RefreshTokenPropertiesTestConfig {
		@Bean
		RefreshTokenProperties refreshTokenProperties() {
			return new RefreshTokenProperties(30, true, "None", "refresh_token", "/api/auth");
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private RefreshTokenService refreshTokenService;

	@BeforeEach
	void stubTokenIssuance() {
		when(refreshTokenService.issue(any()))
				.thenReturn(new RefreshTokenService.IssuedToken("issued-raw-token",
						Instant.now().plus(30, ChronoUnit.DAYS)));
	}

	// JwtAuthFilter is a @Component implementing Filter, so @WebMvcTest auto-detects
	// and constructs it regardless of the classes={...} narrowing — Filter beans are
	// never excluded from the web-layer slice, even when unrelated to the controller
	// under test. It requires a JwtService to construct; mocking it here only satisfies
	// that constructor dependency. The filter itself never runs during these tests,
	// since addFilters = false above disables filter execution entirely.
	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private RateLimiterService rateLimiterService;

	@MockitoBean
	private RateLimitProperties rateLimitProperties;

	private static final String REGISTER_JSON =
			"{\"username\":\"tanish\",\"email\":\"tanish@example.com\",\"password\":\"password123\"}";

	private static final String LOGIN_JSON =
			"{\"email\":\"tanish@example.com\",\"password\":\"password123\"}";

	@Test
	void register_valid_returns201WithAuthResponse() throws Exception {
		AuthResponse response = new AuthResponse("jwt-token", "Bearer", 1L, "tanish", Instant.now());
		when(authService.register(any(RegisterRequest.class))).thenReturn(response);

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(REGISTER_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").value("jwt-token"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.userId").value(1))
				.andExpect(jsonPath("$.username").value("tanish"));
	}

	@Test
	void register_invalidEmail_returns400WithFieldErrors() throws Exception {
		String badEmailJson = "{\"username\":\"tanish\",\"email\":\"not-an-email\",\"password\":\"password123\"}";

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(badEmailJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
	}

	@Test
	void register_duplicateEmail_returns409() throws Exception {
		when(authService.register(any(RegisterRequest.class)))
				.thenThrow(new DuplicateEmailException("tanish@example.com"));

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(REGISTER_JSON))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Email already registered: tanish@example.com"));
	}

	@Test
	void login_valid_returns200WithToken() throws Exception {
		AuthResponse response = new AuthResponse("jwt-token", "Bearer", 1L, "tanish", Instant.now());
		when(authService.login(any(LoginRequest.class))).thenReturn(response);

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(LOGIN_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("jwt-token"));
	}

	@Test
	void login_invalidCredentials_returns401() throws Exception {
		when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(LOGIN_JSON))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void login_malformedEmail_returns400() throws Exception {
		String blankEmailJson = "{\"email\":\"\",\"password\":\"password123\"}";

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(blankEmailJson))
				.andExpect(status().isBadRequest());
	}

	// ---------- refresh (AUTH-04) ----------
	// The full flow lives in AuthControllerIT, which only runs under -Pci. These two cover the
	// CSRF gate and the cookie attributes without Docker, so a regression fails a local build.

	@Test
	void login_attachesHttpOnlyHostOnlyRefreshCookie() throws Exception {
		when(authService.login(any(LoginRequest.class)))
				.thenReturn(new AuthResponse("jwt-token", "Bearer", 1L, "tanish", Instant.now()));

		String setCookie = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(LOGIN_JSON))
				.andExpect(status().isOk())
				.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

		Assertions.assertThat(setCookie)
				.startsWith("refresh_token=issued-raw-token")
				.contains("HttpOnly")
				.contains("Secure")
				.contains("SameSite=None")
				.contains("Path=/api/auth")
				.doesNotContain("Domain=");
	}

	@Test
	void refresh_withoutCustomHeader_returns400AndNeverCallsRotate() throws Exception {
		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie("refresh_token", "presented-token")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));

		verify(refreshTokenService, never()).rotate(any());
	}

	@Test
	void refresh_withHeaderButNoCookie_returns401() throws Exception {
		mockMvc.perform(post("/api/auth/refresh")
				.header("X-Requested-With", "XMLHttpRequest"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));

		verify(refreshTokenService, never()).rotate(any());
	}

	@Test
	void refresh_withCookieAndHeader_returnsNewTokenAndRotatedCookie() throws Exception {
		Instant accessExpiry = Instant.now().plusSeconds(900);
		when(refreshTokenService.rotate("presented-token")).thenReturn(new RefreshTokenService.RotatedSession(
				"new-jwt", accessExpiry, "rotated-raw-token", Instant.now().plus(30, ChronoUnit.DAYS)));

		String setCookie = mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie("refresh_token", "presented-token"))
				.header("X-Requested-With", "XMLHttpRequest"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("new-jwt"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresAt").isNotEmpty())
				.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

		Assertions.assertThat(setCookie).startsWith("refresh_token=rotated-raw-token");
	}
}
