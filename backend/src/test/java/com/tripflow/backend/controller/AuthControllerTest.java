package com.tripflow.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripflow.backend.dto.AuthResponse;
import com.tripflow.backend.dto.LoginRequest;
import com.tripflow.backend.dto.RegisterRequest;
import com.tripflow.backend.exception.DuplicateEmailException;
import com.tripflow.backend.exception.InvalidCredentialsException;
import com.tripflow.backend.security.JwtService;
import com.tripflow.backend.service.AuthService;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	// JwtAuthFilter is a @Component implementing Filter, so @WebMvcTest auto-detects
	// and constructs it regardless of the classes={...} narrowing — Filter beans are
	// never excluded from the web-layer slice, even when unrelated to the controller
	// under test. It requires a JwtService to construct; mocking it here only satisfies
	// that constructor dependency. The filter itself never runs during these tests,
	// since addFilters = false above disables filter execution entirely.
	@MockitoBean
	private JwtService jwtService;

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
}
