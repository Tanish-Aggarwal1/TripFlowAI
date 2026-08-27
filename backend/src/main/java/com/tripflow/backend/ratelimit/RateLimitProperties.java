package com.tripflow.backend.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Per-user request caps on the endpoints that call paid external APIs (SCRUM-173),
 * bound from app.ratelimit.ai-suggest.* / app.ratelimit.optimize.* / app.ratelimit.ai-generate.*
 * so limits can be tuned without a redeploy.
 */
@Validated
@ConfigurationProperties(prefix = "app.ratelimit")
public record RateLimitProperties(@NotNull @Valid Limit aiSuggest, @NotNull @Valid Limit optimize,
		@NotNull @Valid Limit aiGenerate, @NotNull @Valid Limit login, @NotNull @Valid Limit register,
		@NotNull @Valid Limit refresh, @NotNull @Valid Limit discoverySearch, @NotNull @Valid Limit photoSignature,
		@NotNull @Valid Limit tripCreate, @NotNull @Valid Limit tripClone) {

	public record Limit(int capacity, @NotNull Duration window) {
	}
}
