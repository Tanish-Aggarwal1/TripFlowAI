package com.tripflow.backend.exception;

/** General-purpose 400 for request-shape problems not covered by bean validation
 * (e.g. a required query param that's present but blank). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
