package com.tripflow.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.domain.User;
import com.tripflow.backend.dto.AuthResponse;
import com.tripflow.backend.dto.LoginRequest;
import com.tripflow.backend.dto.RegisterRequest;
import com.tripflow.backend.exception.DuplicateEmailException;
import com.tripflow.backend.exception.DuplicateUsernameException;
import com.tripflow.backend.exception.InvalidCredentialsException;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthResponse register(RegisterRequest request) {
		String username = request.username().trim();

		if (userRepository.existsByEmail(request.email())) {
			throw new DuplicateEmailException(request.email());
		}
		if (userRepository.existsByUsername(username)) {
			throw new DuplicateUsernameException(username);
		}

		User user = new User();
		user.setUsername(username);
		user.setEmail(request.email());
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		userRepository.save(user);

		log.info("User registered id={} username={}", user.getId(), user.getUsername());
		return buildAuthResponse(user);
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email()).orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		log.info("User logged in id={} username={}", user.getId(), user.getUsername());
		return buildAuthResponse(user);
	}

	private AuthResponse buildAuthResponse(User user) {
		String token = jwtService.generateToken(user.getId(), user.getEmail());
		return new AuthResponse(token, "Bearer", user.getId(), user.getUsername(), jwtService.getExpiry(token));
	}
}