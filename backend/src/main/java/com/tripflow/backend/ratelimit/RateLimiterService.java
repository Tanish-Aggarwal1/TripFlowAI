package com.tripflow.backend.ratelimit;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

/**
 * In-memory, per-key token buckets (Bucket4j) backing the per-user request caps on
 * the Gemini/ORS endpoints (SCRUM-173). One bucket per (endpoint, userId/IP) key.
 * Bounded by a Caffeine cache (SCRUM-482) rather than held for the JVM's lifetime:
 * AuthController keys on the caller's remote address, so an unbounded map would let
 * an attacker rotating source IPs against /api/auth/** grow this without limit. An
 * evicted bucket simply refills on next use, and idle-for-2h already exceeds every
 * configured window (max 1h), so eviction never resets a bucket a caller still cares
 * about. A multi-instance deployment would need a distributed backend (Bucket4j
 * supports one via Redis/Hazelcast) instead of this in-memory cache.
 */
@Service
public class RateLimiterService {

	private static final long MAX_BUCKETS = 100_000;
	private static final Duration EXPIRE_AFTER_ACCESS = Duration.ofHours(2);

	private final Cache<String, Bucket> buckets;

	public RateLimiterService() {
		this(Caffeine.newBuilder().maximumSize(MAX_BUCKETS).expireAfterAccess(EXPIRE_AFTER_ACCESS).build());
	}

	RateLimiterService(Cache<String, Bucket> buckets) {
		this.buckets = buckets;
	}

	/**
	 * Consumes one token from the bucket identified by {@code key}, creating it with
	 * the given limit on first use. Throws RateLimitExceededException if the bucket
	 * is empty, rather than returning a boolean, so callers can't forget to check it.
	 */
	public void checkLimit(String key, RateLimitProperties.Limit limit) {
		Bucket bucket = buckets.get(key, k -> newBucket(limit));
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (!probe.isConsumed()) {
			long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
			throw new RateLimitExceededException(
					"Rate limit exceeded, try again in " + retryAfterSeconds + " second(s)", retryAfterSeconds);
		}
	}

	private Bucket newBucket(RateLimitProperties.Limit limit) {
		Bandwidth bandwidth = Bandwidth.builder()
				.capacity(limit.capacity())
				.refillIntervally(limit.capacity(), limit.window())
				.build();
		return Bucket.builder().addLimit(bandwidth).build();
	}
}
