package com.tripflow.backend.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

/**
 * In-memory, per-key token buckets (Bucket4j) backing the per-user request caps on
 * the Gemini/ORS endpoints (SCRUM-173). One bucket per (endpoint, userId) key, held
 * for the lifetime of the JVM — fine for this single-instance deployment; a
 * multi-instance deployment would need a distributed backend (Bucket4j supports one
 * via Redis/Hazelcast) instead of this in-memory map.
 */
@Service
public class RateLimiterService {

	private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

	/**
	 * Consumes one token from the bucket identified by {@code key}, creating it with
	 * the given limit on first use. Throws RateLimitExceededException if the bucket
	 * is empty, rather than returning a boolean, so callers can't forget to check it.
	 */
	public void checkLimit(String key, RateLimitProperties.Limit limit) {
		Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(limit));
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
