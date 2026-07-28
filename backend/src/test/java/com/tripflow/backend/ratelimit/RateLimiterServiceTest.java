package com.tripflow.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RateLimiterServiceTest {

	private final RateLimiterService rateLimiter = new RateLimiterService();

	@Test
	void checkLimit_allowsUpToCapacityThenRejectsTheNextOne() {
		RateLimitProperties.Limit limit = new RateLimitProperties.Limit(3, Duration.ofHours(1));

		rateLimiter.checkLimit("user-1", limit);
		rateLimiter.checkLimit("user-1", limit);
		rateLimiter.checkLimit("user-1", limit);

		assertThatThrownBy(() -> rateLimiter.checkLimit("user-1", limit))
				.isInstanceOf(RateLimitExceededException.class);
	}

	@Test
	void checkLimit_isScopedPerKey() {
		RateLimitProperties.Limit limit = new RateLimitProperties.Limit(1, Duration.ofHours(1));

		rateLimiter.checkLimit("user-a", limit);
		// A different key (different user/endpoint) has its own independent bucket.
		rateLimiter.checkLimit("user-b", limit);

		assertThatThrownBy(() -> rateLimiter.checkLimit("user-a", limit))
				.isInstanceOf(RateLimitExceededException.class);
	}

	@Test
	void checkLimit_exceptionCarriesPositiveRetryAfterSeconds() {
		RateLimitProperties.Limit limit = new RateLimitProperties.Limit(1, Duration.ofHours(1));

		rateLimiter.checkLimit("user-x", limit);

		assertThatThrownBy(() -> rateLimiter.checkLimit("user-x", limit))
				.isInstanceOf(RateLimitExceededException.class)
				.satisfies(ex -> assertThat(((RateLimitExceededException) ex).getRetryAfterSeconds()).isPositive());
	}

	@Test
	void checkLimit_resetsAfterTheConfiguredWindowElapses() throws InterruptedException {
		RateLimitProperties.Limit limit = new RateLimitProperties.Limit(1, Duration.ofMillis(50));

		rateLimiter.checkLimit("user-reset", limit);
		assertThatThrownBy(() -> rateLimiter.checkLimit("user-reset", limit))
				.isInstanceOf(RateLimitExceededException.class);

		Thread.sleep(100);

		// Bucket refilled after the window elapsed — must not throw.
		rateLimiter.checkLimit("user-reset", limit);
	}
}
