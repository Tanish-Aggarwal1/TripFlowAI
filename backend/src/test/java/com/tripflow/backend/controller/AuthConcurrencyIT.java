package com.tripflow.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.dto.RegisterRequest;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

/**
 * SCRUM-263 regression coverage: {@link com.tripflow.backend.service.AuthService#register}
 * falls back to matching a real Postgres unique-constraint name
 * ({@code users_email_key} / {@code users_username_key}, hardcoded from
 * {@code V1__create_users.sql}) when two requests race past the exists-check
 * pre-checks and both reach {@code save()}. {@link com.tripflow.backend.service.AuthServiceTest}
 * only proves the string-matching logic against a hand-typed constraint name in a mock —
 * it can't catch a future migration silently renaming the real constraint. This test forces
 * a genuine race against Testcontainers Postgres so the fallback is checked against the
 * constraint name Postgres actually generates, not a string literal a unit test assumes.
 *
 * <p>No class-level {@code @Transactional} — each request must commit on its own connection
 * for the two threads to genuinely race at the database, same reasoning as
 * {@link RouteOptimizationConcurrencyIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthConcurrencyIT {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private int register(String username, String email, CountDownLatch ready, CountDownLatch go) throws Exception {
		ready.countDown();
		assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();

		String body = objectMapper.writeValueAsString(new RegisterRequest(username, email, "password123"));
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andReturn().getResponse().getStatus();
	}

	private List<Integer> raceTwoRegistrations(CountDownLatch ready, CountDownLatch go,
			Callable<Integer> first, Callable<Integer> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Integer> a = executor.submit(first);
			Future<Integer> b = executor.submit(second);

			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			go.countDown();

			return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void register_concurrentRequestsWithSameEmail_exactlyOneSucceeds() throws Exception {
		String email = "race-email-" + System.nanoTime() + "@tripflow.com";
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);

		List<Integer> statuses = raceTwoRegistrations(ready, go,
				() -> register("race-email-a", email, ready, go),
				() -> register("race-email-b", email, ready, go));

		assertThat(statuses).containsExactlyInAnyOrder(201, 409);
	}

	@Test
	void register_concurrentRequestsWithSameUsername_exactlyOneSucceeds() throws Exception {
		String username = "race-username-" + System.nanoTime();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);

		List<Integer> statuses = raceTwoRegistrations(ready, go,
				() -> register(username, "race-username-a-" + System.nanoTime() + "@tripflow.com", ready, go),
				() -> register(username, "race-username-b-" + System.nanoTime() + "@tripflow.com", ready, go));

		assertThat(statuses).containsExactlyInAnyOrder(201, 409);
	}
}
