package com.tripflow.backend.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;

/**
 * fieldErrors is null for every non-validation error (see GlobalExceptionHandler /
 * SecurityErrorWriter) — NON_NULL here makes docs/api-contracts.md's "null/omitted otherwise"
 * literal by omitting the key entirely rather than serializing it as {@code null}. Applies to
 * both mappers that serialize this class: the Jackson 3 mapper autoconfigured for MVC responses
 * and the Jackson 2 ObjectMapper SecurityErrorWriter uses (Jackson 3 depends on the same
 * com.fasterxml.jackson.annotation package for annotations).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
