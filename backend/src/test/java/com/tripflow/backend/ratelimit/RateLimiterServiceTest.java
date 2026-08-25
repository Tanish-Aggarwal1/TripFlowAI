package com.tripflow.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bucket;

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

	@Test
	void checkLimit_evictsOldestBucketsOncePastTheConfiguredCacheSize() {
		// SCRUM-482 regression: on unbounded storage this cache would keep growing
		// forever (e.g. one entry per attacker-rotated source IP hitting
		// /api/auth/login). A bounded cache must actually shed entries under pressure.
		Cache<String, Bucket> boundedCache = Caffeine.newBuilder().maximumSize(2).build();
		RateLimiterService bounded = new RateLimiterService(boundedCache);
		RateLimitProperties.Limit limit = new RateLimitProperties.Limit(1, Duration.ofHours(1));

		bounded.checkLimit("key-1", limit);
		bounded.checkLimit("key-2", limit);
		bounded.checkLimit("key-3", limit);
		boundedCache.cleanUp();

		assertThat(boundedCache.estimatedSize()).isLessThanOrEqualTo(2);
	}

	@Test
	void springResolvesTheNoArgConstructorDespiteTheTestOnlyConstructor() {
		// The package-private Cache-accepting constructor exists only so this test
		// class can inject a small bounded cache above. Spring must still autowire
		// the bean via the public no-arg constructor in production (no Docker
		// container available in this environment to prove it via a full
		// @SpringBootTest, so assert against the exact resolution utility Spring's
		// AutowiredAnnotationBeanPostProcessor falls back to when no constructor is
		// @Autowired: BeanUtils.getResolvableConstructor).
		var resolved = BeanUtils.getResolvableConstructor(RateLimiterService.class);

		assertThat(resolved.getParameterCount()).isZero();
	}
}
