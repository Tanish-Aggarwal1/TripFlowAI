package com.tripflow.backend.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AuthUtilsTest {

	@Test
	void currentUserId_authenticated_returnsParsedLong() {
		var auth = UsernamePasswordAuthenticationToken.authenticated("42", null, List.of());

		assertThat(AuthUtils.currentUserId(auth)).isEqualTo(42L);
	}

	@Test
	void currentUserId_nullAuthentication_throwsIllegalStateException() {
		assertThatThrownBy(() -> AuthUtils.currentUserId(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("No authenticated user");
	}

	@Test
	void currentUserId_unauthenticated_throwsIllegalStateException() {
		var auth = UsernamePasswordAuthenticationToken.unauthenticated("42", null);

		assertThatThrownBy(() -> AuthUtils.currentUserId(auth))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("No authenticated user");
	}
}
