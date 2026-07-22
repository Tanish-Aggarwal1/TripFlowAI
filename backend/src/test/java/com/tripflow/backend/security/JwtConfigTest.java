package com.tripflow.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = JwtConfig.class)
@ActiveProfiles("test")
class JwtConfigTest {

	@Autowired
	private JwtProperties jwtProperties;

	@Test
	void propertiesBindFromApplicationProperties() {
		assertThat(jwtProperties.secret())
				.isEqualTo("test-jwt-secret-must-be-at-least-256-bits-long-for-hmac-sha256");
		assertThat(jwtProperties.expirationMs()).isEqualTo(3_600_000L);
	}
}
