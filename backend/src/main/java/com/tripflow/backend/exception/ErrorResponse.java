package com.tripflow.backend.exception;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String message,
        List<Map<String, String>> fieldErrors
) {
    public ErrorResponse(int status, String message) {
        this(Instant.now(), status, message, List.of());
    }

    public ErrorResponse(int status, String message, List<Map<String, String>> fieldErrors) {
        this(Instant.now(), status, message, fieldErrors);
    }
}