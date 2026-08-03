package com.tripflow.backend.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-user request caps on the endpoints that call paid external APIs (SCRUM-173),
 * bound from app.ratelimit.ai-suggest.* / app.ratelimit.optimize.* / app.ratelimit.ai-generate.*
 * so limits can be tuned without a redeploy.
 */
@ConfigurationProperties(prefix = "app.ratelimit")
public record RateLimitProperties(Limit aiSuggest, Limit optimize, Limit aiGenerate) {

	public record Limit(int capacity, Duration window) {
	}
}
