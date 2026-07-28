package com.tripflow.backend.ratelimit;

import lombok.Getter;

/**
 * Thrown by RateLimiterService when a user's request bucket for a given endpoint is
 * empty. Translated to 429 (with a Retry-After header) by GlobalExceptionHandler.
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

	private final long retryAfterSeconds;

	public RateLimitExceededException(String message, long retryAfterSeconds) {
		super(message);
		this.retryAfterSeconds = retryAfterSeconds;
	}
}
