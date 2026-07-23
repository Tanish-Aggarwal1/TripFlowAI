package com.tripflow.backend.exception;

public class OrsRateLimitException extends OrsClientException {
	public OrsRateLimitException(String message, Throwable cause) {
		super(message, cause);
	}
}
