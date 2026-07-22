package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tripflow.backend.domain.User;
import com.tripflow.backend.dto.AuthResponse;
import com.tripflow.backend.dto.LoginRequest;
import com.tripflow.backend.dto.RegisterRequest;
import com.tripflow.backend.exception.DuplicateEmailException;
import com.tripflow.backend.exception.DuplicateUsernameException;
import com.tripflow.backend.exception.InvalidCredentialsException;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final String EMAIL = "user@example.com";
	private static final String USERNAME = "testuser";
	private static final String PASSWORD = "password123";
	private static final String HASHED = "$2a$10$hashedpassword";
	private static final String TOKEN = "jwt-token";
	private static final Instant EXPIRY = Instant.parse("2026-12-31T23:59:59Z");

	@Mock private UserRepository userRepository;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private JwtService jwtService;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(userRepository, passwordEncoder, jwtService);
	}

	@Test
	void register_happyPath_encodesPasswordAndReturnsBearerResponse() {
		RegisterRequest request = new RegisterRequest(USERNAME, EMAIL, PASSWORD);
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
		when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED);
		when(userRepository.save(any(User.class))).thenAnswer(inv -> {
			User user = inv.getArgument(0, User.class);
			user.setId(42L);
			return user;
		});
		when(jwtService.generateToken(42L, EMAIL)).thenReturn(TOKEN);
		when(jwtService.getExpiry(TOKEN)).thenReturn(EXPIRY);

		AuthResponse response = authService.register(request);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		User saved = userCaptor.getValue();
		assertThat(saved.getUsername()).isEqualTo(USERNAME);
		assertThat(saved.getEmail()).isEqualTo(EMAIL);
		assertThat(saved.getPasswordHash()).isEqualTo(HASHED);

		assertThat(response.token()).isEqualTo(TOKEN);
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.userId()).isEqualTo(42L);
		assertThat(response.username()).isEqualTo(USERNAME);
		assertThat(response.expiresAt()).isEqualTo(EXPIRY);
	}
	
	@Test
	void register_usernameWithSurroundingWhitespace_trimsBeforeCheckAndPersist() {
		String padded = "  " + USERNAME + "  ";
		RegisterRequest request = new RegisterRequest(padded, EMAIL, PASSWORD);
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(userRepository.existsByUsername(USERNAME)).thenReturn(false);
		when(passwordEncoder.encode(PASSWORD)).thenReturn(HASHED);
		when(userRepository.save(any(User.class))).thenAnswer(inv -> {
			User user = inv.getArgument(0, User.class);
			user.setId(42L);
			return user;
		});
		when(jwtService.generateToken(42L, EMAIL)).thenReturn(TOKEN);
		when(jwtService.getExpiry(TOKEN)).thenReturn(EXPIRY);

		authService.register(request);

		// The duplicate check must run against the trimmed value too - otherwise
		// "user" and "user " could both sail past existsByUsername and collide at
		// the DB's unique constraint instead of a clean DuplicateUsernameException.
		verify(userRepository).existsByUsername(USERNAME);
		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		assertThat(userCaptor.getValue().getUsername()).isEqualTo(USERNAME);
	}

	@Test
	void register_duplicateEmail_throwsDuplicateEmailException() {
		RegisterRequest request = new RegisterRequest(USERNAME, EMAIL, PASSWORD);
		when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

		assertThatThrownBy(() -> authService.register(request))
				.isInstanceOf(DuplicateEmailException.class);

		verify(userRepository, never()).save(any());
	}

	@Test
	void register_duplicateUsername_throwsDuplicateUsernameException() {
		RegisterRequest request = new RegisterRequest(USERNAME, EMAIL, PASSWORD);
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(userRepository.existsByUsername(USERNAME)).thenReturn(true);

		assertThatThrownBy(() -> authService.register(request))
				.isInstanceOf(DuplicateUsernameException.class);

		verify(userRepository, never()).save(any());
	}

	@Test
	void login_happyPath_returnsBearerResponse() {
		LoginRequest request = new LoginRequest(EMAIL, PASSWORD);
		User user = new User();
		user.setId(7L);
		user.setUsername(USERNAME);
		user.setEmail(EMAIL);
		user.setPasswordHash(HASHED);
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(true);
		when(jwtService.generateToken(7L, EMAIL)).thenReturn(TOKEN);
		when(jwtService.getExpiry(TOKEN)).thenReturn(EXPIRY);

		AuthResponse response = authService.login(request);

		assertThat(response.token()).isEqualTo(TOKEN);
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.userId()).isEqualTo(7L);
		assertThat(response.username()).isEqualTo(USERNAME);
		assertThat(response.expiresAt()).isEqualTo(EXPIRY);
	}

	@Test
	void login_unknownEmail_throwsInvalidCredentialsException() {
		LoginRequest request = new LoginRequest(EMAIL, PASSWORD);
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(request))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void login_wrongPassword_throwsInvalidCredentialsException() {
		LoginRequest request = new LoginRequest(EMAIL, PASSWORD);
		User user = new User();
		user.setEmail(EMAIL);
		user.setPasswordHash(HASHED);
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(PASSWORD, HASHED)).thenReturn(false);

		assertThatThrownBy(() -> authService.login(request))
				.isInstanceOf(InvalidCredentialsException.class);
	}
}
