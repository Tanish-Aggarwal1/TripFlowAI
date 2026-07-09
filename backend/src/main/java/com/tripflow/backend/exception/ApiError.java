package com.tripflow.backend.exception;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;

@Getter
public class ApiError {
	private final Instant timestamp = Instant.now();
    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final List<FieldError> fieldErrors;

    public ApiError(int status, String error, String message, String path, List<FieldError> fieldErrors) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors;
    }

    @Getter
    public static class FieldError {
        private final String field;
        private final String message;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }
    }
}
