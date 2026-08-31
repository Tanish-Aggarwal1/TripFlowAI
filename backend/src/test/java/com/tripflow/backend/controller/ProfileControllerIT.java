package com.tripflow.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
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

	private final ObjectMapper objectMapper = new ObjectMapper();

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

	@Test
	void updateInterests_validArray_replacesWholesaleAndReturnsUpdatedProfile() throws Exception {
		User user = createTestUser("update1");

		mockMvc.perform(patch("/api/profile/interests").with(csrf()).with(asUser(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new InterestsBody(List.of("hiking", "food")))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.interests.length()").value(2))
				.andExpect(jsonPath("$.interests[0]").value("hiking"))
				.andExpect(jsonPath("$.interests[1]").value("food"));

		mockMvc.perform(get("/api/profile").with(asUser(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.interests.length()").value(2));
	}

	@Test
	void updateInterests_emptyArray_clearsInterests() throws Exception {
		User user = createTestUser("update2");
		mockMvc.perform(patch("/api/profile/interests").with(csrf()).with(asUser(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new InterestsBody(List.of("hiking")))))
				.andExpect(status().isOk());

		mockMvc.perform(patch("/api/profile/interests").with(csrf()).with(asUser(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new InterestsBody(List.of()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.interests.length()").value(0));
	}

	@Test
	void updateInterests_nullInterests_returns400() throws Exception {
		mockMvc.perform(patch("/api/profile/interests").with(csrf()).with(asUser(createTestUser("nullint")))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"interests\":null}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("interests"));
	}

	@Test
	void updateInterests_moreThan20Elements_returns400WithFieldError() throws Exception {
		List<String> tooMany = java.util.stream.IntStream.range(0, 21).mapToObj(i -> "interest" + i).toList();

		mockMvc.perform(patch("/api/profile/interests").with(csrf()).with(asUser(createTestUser("toomany")))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new InterestsBody(tooMany))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors").isNotEmpty())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("interests"));
	}

	@Test
	void updateInterests_elementOver50Chars_returns400() throws Exception {
		String tooLong = "x".repeat(51);

		mockMvc.perform(patch("/api/profile/interests").with(csrf()).with(asUser(createTestUser("toolong")))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new InterestsBody(List.of(tooLong)))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors").isNotEmpty());
	}

	@Test
	void updateInterests_cannotRetargetAnotherUser() throws Exception {
		User userA = createTestUser("scopeA");
		User userB = createTestUser("scopeB");

		mockMvc.perform(patch("/api/profile/interests").with(csrf()).with(asUser(userA))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new InterestsBody(List.of("only-a")))))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/profile").with(asUser(userB)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.interests.length()").value(0));
	}

	private record InterestsBody(List<String> interests) {
	}
}
