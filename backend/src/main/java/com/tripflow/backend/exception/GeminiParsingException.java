package com.tripflow.backend.exception;

/**
 * Raised when Gemini's response text is not valid JSON, or doesn't match the
 * strict SuggestedItinerary schema. Translated to 502 Bad Gateway.
 */
public class GeminiParsingException extends RuntimeException {

    public GeminiParsingException(String message) {
        super(message);
    }

    public GeminiParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}