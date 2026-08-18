package com.tripflow.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;

/**
 * End-to-end tests for {@code /api/auth/**} against the real Spring context,
 * security filter chain, and a Testcontainers Postgres 16 instance (no mocks) —
 * the layer {@link com.tripflow.backend.service.AuthServiceTest} intentionally
 * doesn't cover.
 *
 * Runs only under the {@code ci} Maven profile via Failsafe (see REF-05 / SCRUM-91).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AuthControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private static final String REFRESH_COOKIE = "refresh_token";
	private static final String CSRF_HEADER = "X-Requested-With";

	// ---------- helpers ----------

	/** The raw {@code Set-Cookie} line for the refresh cookie, attribute string included. */
	private String refreshSetCookieHeader(MockHttpServletResponse response) {
		return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
				.filter(header -> header.startsWith(REFRESH_COOKIE + "="))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no " + REFRESH_COOKIE + " Set-Cookie header on the response"));
	}

	private String refreshCookieValue(MockHttpServletResponse response) {
		String header = refreshSetCookieHeader(response);
		int start = REFRESH_COOKIE.length() + 1;
		int end = header.indexOf(';', start);
		return end < 0 ? header.substring(start) : header.substring(start, end);
	}

	private String registerJson(String username, String email, String password) throws Exception {
		return objectMapper.writeValueAsString(new com.tripflow.backend.dto.RegisterRequest(username, email, password));
	}

	private String loginJson(String email, String password) throws Exception {
		return objectMapper.writeValueAsString(new com.tripflow.backend.dto.LoginRequest(email, password));
	}

	private User persistUser(String username, String email, String rawPassword) {
		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(rawPassword));
		return userRepository.save(user);
	}

	// ---------- register ----------

	@Test
	void register_validRequest_returns201WithTokenAndNoPasswordLeak() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson("tanish", "tanish@tripflow.com", "password123")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.userId").isNumber())
				.andExpect(jsonPath("$.username").value("tanish"))
				.andExpect(jsonPath("$.expiresAt").isNotEmpty())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void register_persistsUserWithEncodedPassword() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson("neel", "neel@tripflow.com", "password123")))
				.andExpect(status().isCreated());

		User saved = userRepository.findByEmail("neel@tripflow.com").orElseThrow();
		org.assertj.core.api.Assertions.assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
		org.assertj.core.api.Assertions.assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
	}

	@Test
	void register_duplicateEmail_returns409() throws Exception {
		persistUser("existing", "taken@tripflow.com", "password123");

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson("someoneelse", "taken@tripflow.com", "password123")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value("Email already registered: taken@tripflow.com"));
	}

	@Test
	void register_duplicateUsername_returns409() throws Exception {
		persistUser("pratham", "pratham-original@tripflow.com", "password123");

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson("pratham", "pratham-new@tripflow.com", "password123")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value("Username already taken: pratham"));
	}

	@Test
	void register_blankUsername_returns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson("", "joann@tripflow.com", "password123")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItem("username")));
	}

	@Test
	void register_invalidEmail_returns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson("joann", "not-an-email", "password123")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItem("email")));
	}

	@Test
	void register_shortPassword_returns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson("joann", "joann@tripflow.com", "short")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItem("password")));
	}

	@Test
	void register_passwordOver72Bytes_returns400NotBCryptError() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson("joann", "joann@tripflow.com", "a".repeat(100))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItem("password")));
	}

	// ---------- login ----------

	@Test
	void login_validCredentials_returns200WithToken() throws Exception {
		persistUser("tanish", "tanish@tripflow.com", "password123");

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson("tanish@tripflow.com", "password123")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.username").value("tanish"));
	}

	@Test
	void login_wrongPassword_returns401() throws Exception {
		persistUser("tanish", "tanish@tripflow.com", "password123");

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson("tanish@tripflow.com", "wrong-password")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void login_unknownEmail_returns401WithSameMessageAsWrongPassword() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson("ghost@tripflow.com", "password123")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void login_blankCredentials_returns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson("", "")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	// ---------- protected-route boundary ----------

	@Test
	void authEndpoints_reachableWithoutBearerToken() throws Exception {
		// /api/auth/** is permitAll in SecurityConfig — this pins that contract
		// so a future security change can't silently lock out registration/login.
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson("noauthheader", "noauthheader@tripflow.com", "password123")))
				.andExpect(status().isCreated());
	}

	// ---------- register-then-login flow ----------

	@Test
	void registerThenLogin_usesSameCredentials_succeeds() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson("joann", "joann@tripflow.com", "password123")))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson("joann@tripflow.com", "password123")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("joann"));
	}
	
	// ---------- real JWT end-to-end path ----------

		@Test
		void registeredToken_grantsAccessToProtectedEndpoint() throws Exception {
			String response = mockMvc.perform(post("/api/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content(registerJson("pratham", "pratham@tripflow.com", "password123")))
					.andExpect(status().isCreated())
					.andReturn().getResponse().getContentAsString();

			String token = objectMapper.readTree(response).get("token").asText();

			mockMvc.perform(get("/api/trips")
					.header("Authorization", "Bearer " + token))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content").isArray())
					.andExpect(jsonPath("$.content").isEmpty())
					.andExpect(jsonPath("$.page.totalElements").value(0));
		}

		@Test
		void loggedInToken_grantsAccessToProtectedEndpoint() throws Exception {
			persistUser("neel", "neel@tripflow.com", "password123");

			String response = mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(loginJson("neel@tripflow.com", "password123")))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();

			String token = objectMapper.readTree(response).get("token").asText();

			mockMvc.perform(get("/api/trips")
					.header("Authorization", "Bearer " + token))
					.andExpect(status().isOk());
		}

	// ---------- refresh-token flow (AUTH-04) ----------

	@Test
	void login_setsHttpOnlyRefreshTokenCookie() throws Exception {
		persistUser("tanish", "tanish@tripflow.com", "password123");

		MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson("tanish@tripflow.com", "password123")))
				.andExpect(status().isOk())
				// the frozen AuthResponse body shape is unchanged by the cookie
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.userId").isNumber())
				.andExpect(jsonPath("$.username").value("tanish"))
				.andExpect(jsonPath("$.expiresAt").isNotEmpty())
				.andReturn().getResponse();

		Assertions.assertThat(refreshSetCookieHeader(response))
				.contains("HttpOnly")
				.contains("Secure")
				.contains("SameSite=None")
				.contains("Path=/api/auth")
				// a Domain attribute would expose the cookie to every other tenant on the PaaS suffix
				.doesNotContain("Domain=");
	}

	@Test
	void login_refreshTokenValueIsNotInResponseBody() throws Exception {
		persistUser("tanish", "tanish@tripflow.com", "password123");

		MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson("tanish@tripflow.com", "password123")))
				.andExpect(status().isOk())
				.andReturn().getResponse();

		Assertions.assertThat(response.getContentAsString()).doesNotContain(refreshCookieValue(response));
	}

	@Test
	void refresh_withValidCookie_returnsNewAccessTokenAndRotatesCookie() throws Exception {
		persistUser("tanish", "tanish@tripflow.com", "password123");

		String issued = refreshCookieValue(mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson("tanish@tripflow.com", "password123")))
				.andExpect(status().isOk())
				.andReturn().getResponse());

		MockHttpServletResponse response = mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie(REFRESH_COOKIE, issued))
				.header(CSRF_HEADER, "XMLHttpRequest"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresAt").isNotEmpty())
				.andReturn().getResponse();

		Assertions.assertThat(refreshCookieValue(response)).isNotBlank().isNotEqualTo(issued);
	}

	@Test
	void refresh_withoutCustomHeader_returns400BeforeAnyTokenLookup() throws Exception {
		persistUser("tanish", "tanish@tripflow.com", "password123");

		String issued = refreshCookieValue(mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson("tanish@tripflow.com", "password123")))
				.andExpect(status().isOk())
				.andReturn().getResponse());

		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie(REFRESH_COOKIE, issued)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.path").value("/api/auth/refresh"));
	}

	@Test
	void refresh_withNoCookie_returns401() throws Exception {
		mockMvc.perform(post("/api/auth/refresh")
				.header(CSRF_HEADER, "XMLHttpRequest"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.path").value("/api/auth/refresh"));
	}

	@Test
	void refreshTokensTable_storesOnlyTheHash() throws Exception {
		User user = persistUser("tanish", "tanish@tripflow.com", "password123");

		String issued = refreshCookieValue(mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson("tanish@tripflow.com", "password123")))
				.andExpect(status().isOk())
				.andReturn().getResponse());

		// the raw JdbcTemplate count below bypasses Hibernate's auto-flush
		entityManager.flush();

		Assertions.assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?", Long.class, issued)).isZero();
		Assertions.assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Long.class, user.getId())).isEqualTo(1L);
	}

	// ---------- reuse detection and logout (AUTH-04, D-03/D-04) ----------

	private String loginAndCaptureRefreshCookie(String email, String password) throws Exception {
		return refreshCookieValue(mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson(email, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse());
	}

	private long unrevokedTokenCount(Long userId) {
		entityManager.flush();
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Long.class, userId);
	}

	/** No flush, because this runs outside a transaction — the endpoints commit their own. */
	private long committedUnrevokedTokenCount(Long userId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Long.class, userId);
	}

	/**
	 * Deliberately outside the class-level test transaction. Joined to it, the service's own
	 * {@code @Transactional} would only mark the shared transaction rollback-only if rotate()'s
	 * {@code noRollbackFor} were deleted — the JdbcTemplate reads below run on that same
	 * connection, so they would still observe the uncommitted revocations and this test would
	 * pass while the load-bearing annotation was gone. A real commit boundary is the only thing
	 * that makes the mass revoke's survival observable, so the rows are cleaned up by hand.
	 */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void refresh_replayOfAlreadyRotatedCookie_returns401AndRevokesAllUserTokens() throws Exception {
		User user = persistUser("replay", "replay@tripflow.com", "password123");
		try {
			String original = loginAndCaptureRefreshCookie("replay@tripflow.com", "password123");

			mockMvc.perform(post("/api/auth/refresh")
					.cookie(new Cookie(REFRESH_COOKIE, original))
					.header(CSRF_HEADER, "XMLHttpRequest"))
					.andExpect(status().isOk());

			// two rows now, neither revoked: the redeemed original and its live replacement
			Assertions.assertThat(committedUnrevokedTokenCount(user.getId())).isEqualTo(2L);

			mockMvc.perform(post("/api/auth/refresh")
					.cookie(new Cookie(REFRESH_COOKIE, original))
					.header(CSRF_HEADER, "XMLHttpRequest"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.status").value(401))
					.andExpect(jsonPath("$.path").value("/api/auth/refresh"));

			// the replacement was valid a moment ago and is now revoked too — that is the whole of D-03
			Assertions.assertThat(committedUnrevokedTokenCount(user.getId())).isZero();
			Assertions.assertThat(jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Long.class, user.getId())).isEqualTo(2L);
		} finally {
			jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", user.getId());
			jdbcTemplate.update("DELETE FROM users WHERE id = ?", user.getId());
		}
	}

	@Test
	void refresh_afterMassRevoke_evenTheRotatedCookieIsRejected() throws Exception {
		persistUser("tanish", "tanish@tripflow.com", "password123");
		String original = loginAndCaptureRefreshCookie("tanish@tripflow.com", "password123");

		String rotated = refreshCookieValue(mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie(REFRESH_COOKIE, original))
				.header(CSRF_HEADER, "XMLHttpRequest"))
				.andExpect(status().isOk())
				.andReturn().getResponse());

		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie(REFRESH_COOKIE, original))
				.header(CSRF_HEADER, "XMLHttpRequest"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie(REFRESH_COOKIE, rotated))
				.header(CSRF_HEADER, "XMLHttpRequest"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void logout_revokesOnlyThePresentedToken() throws Exception {
		persistUser("tanish", "tanish@tripflow.com", "password123");
		String deviceOne = loginAndCaptureRefreshCookie("tanish@tripflow.com", "password123");
		String deviceTwo = loginAndCaptureRefreshCookie("tanish@tripflow.com", "password123");

		mockMvc.perform(post("/api/auth/logout")
				.cookie(new Cookie(REFRESH_COOKIE, deviceOne))
				.header(CSRF_HEADER, "XMLHttpRequest"))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie(REFRESH_COOKIE, deviceOne))
				.header(CSRF_HEADER, "XMLHttpRequest"))
				.andExpect(status().isUnauthorized());

		// the other device is untouched — D-04's blast radius is exactly one session
		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie(REFRESH_COOKIE, deviceTwo))
				.header(CSRF_HEADER, "XMLHttpRequest"))
				.andExpect(status().isOk());
	}

	@Test
	void logout_clearsTheCookieWithMatchingAttributes() throws Exception {
		persistUser("tanish", "tanish@tripflow.com", "password123");
		String issued = loginAndCaptureRefreshCookie("tanish@tripflow.com", "password123");

		MockHttpServletResponse response = mockMvc.perform(post("/api/auth/logout")
				.cookie(new Cookie(REFRESH_COOKIE, issued))
				.header(CSRF_HEADER, "XMLHttpRequest"))
				.andExpect(status().isNoContent())
				.andReturn().getResponse();

		// a mismatched path or attribute set makes the browser keep the original cookie
		Assertions.assertThat(refreshSetCookieHeader(response))
				.contains("Max-Age=0")
				.contains("Path=/api/auth")
				.contains("HttpOnly")
				.contains("SameSite=None")
				.doesNotContain("Domain=");
		Assertions.assertThat(refreshCookieValue(response)).isEmpty();
	}

	@Test
	void logout_withNoCookie_returns204() throws Exception {
		mockMvc.perform(post("/api/auth/logout")
				.header(CSRF_HEADER, "XMLHttpRequest"))
				.andExpect(status().isNoContent());
	}

	@Test
	void logout_withoutCustomHeader_isRejected() throws Exception {
		persistUser("tanish", "tanish@tripflow.com", "password123");
		String issued = loginAndCaptureRefreshCookie("tanish@tripflow.com", "password123");

		mockMvc.perform(post("/api/auth/logout")
				.cookie(new Cookie(REFRESH_COOKIE, issued)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.path").value("/api/auth/logout"));

		// the presented token survives a rejected logout
		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie(REFRESH_COOKIE, issued))
				.header(CSRF_HEADER, "XMLHttpRequest"))
				.andExpect(status().isOk());
	}
}
