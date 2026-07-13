package com.tripflow.backend.exception;


/**
 * Raised when OpenRouteService is unreachable, times out, or returns an error.
 * Translated to 502 Bad Gateway by GlobalExceptionHandler.
 */
public class OrsClientException extends RuntimeException {

	public OrsClientException(String message) {
        super(message);
    }

    public OrsClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
