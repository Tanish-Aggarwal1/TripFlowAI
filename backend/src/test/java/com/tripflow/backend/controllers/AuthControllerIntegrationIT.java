package com.tripflow.backend.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.beans.User;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

/**
 * End-to-end tests for {@code /api/auth/**} against the real Spring context,
 * security filter chain, and a Testcontainers Postgres 16 instance (no mocks) —
 * the layer {@link com.tripflow.backend.service.AuthServiceTest} intentionally
 * doesn't cover.
 *
 * Runs only under the {@code ci} Maven profile via Failsafe (see REF-05 / SCRUM-91).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
public class AuthControllerIntegrationIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private final ObjectMapper objectMapper = new ObjectMapper();

	// ---------- helpers ----------

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
}
