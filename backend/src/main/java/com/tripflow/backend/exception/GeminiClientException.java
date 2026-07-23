package com.tripflow.backend.exception;

/**
 * Raised when Gemini is unreachable, times out, or returns an error.
 * Translated to 502 Bad Gateway by GlobalExceptionHandler.
 */
public class GeminiClientException extends RuntimeException {

    public GeminiClientException(String message) {
        super(message);
    }

    public GeminiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}