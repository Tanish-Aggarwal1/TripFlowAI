package com.tripflow.backend.exception;

/**
 * Raised when a rendered AI prompt exceeds {@code ItineraryPromptTemplate.MAX_PROMPT_LENGTH}
 * — a defensive second layer behind {@code ItineraryPreferencesRequest}'s per-field
 * {@code @Size} caps, since a trip's stop count (and therefore total destination text) isn't
 * itself capped. Translated to 400 by {@link GlobalExceptionHandler}.
 */
public class PromptTooLargeException extends RuntimeException {
    public PromptTooLargeException(String message) {
        super(message);
    }
}
