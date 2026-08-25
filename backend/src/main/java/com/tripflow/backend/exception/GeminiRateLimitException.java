package com.tripflow.backend.exception;

public class GeminiRateLimitException extends GeminiClientException {
	public GeminiRateLimitException(String message, Throwable cause) {
		super(message, cause);
	}
}
