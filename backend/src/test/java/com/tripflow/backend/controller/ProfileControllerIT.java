package com.tripflow.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.domain.User;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

/** End-to-end IT for GET/PATCH /api/profile (SOCIAL-05, D-07). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ProfileControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	private User createTestUser(String suffix) {
		User user = new User();
		user.setUsername("profile-" + suffix);
		user.setEmail("profile-" + suffix + "@example.com");
		user.setPasswordHash("hashed");
		return userRepository.save(user);
	}

	private RequestPostProcessor asUser(User user) {
		UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail());
		var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		return authentication(auth);
	}

	@Test
	void getProfile_authenticatedUser_returnsUsernameJoinDateAndEmptyInterests() throws Exception {
		User user = createTestUser("read1");

		mockMvc.perform(get("/api/profile").with(asUser(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value(user.getUsername()))
				.andExpect(jsonPath("$.joinedAt").exists())
				.andExpect(jsonPath("$.interests").isArray())
				.andExpect(jsonPath("$.interests.length()").value(0));
	}

	@Test
	void getProfile_noAuthorizationHeader_returns401() throws Exception {
		mockMvc.perform(get("/api/profile"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void getProfile_twoDistinctUsers_eachReceivesOwnProfile() throws Exception {
		User userA = createTestUser("distinctA");
		User userB = createTestUser("distinctB");

		mockMvc.perform(get("/api/profile").with(asUser(userA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value(userA.getUsername()));

		mockMvc.perform(get("/api/profile").with(asUser(userB)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value(userB.getUsername()));
	}
}
