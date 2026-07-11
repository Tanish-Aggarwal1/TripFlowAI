package com.tripflow.backend.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
		return error(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex, HttpServletRequest req) {
		return error(HttpStatus.FORBIDDEN, ex.getMessage(), req, null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {

		List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage())).toList();

		return error(HttpStatus.BAD_REQUEST, "Validation failed", req, fieldErrors);
	}

	@ExceptionHandler({ DuplicateEmailException.class, DuplicateUsernameException.class })

	public ResponseEntity<ApiError> handleDuplicate(RuntimeException ex, HttpServletRequest req) {
		return error(HttpStatus.CONFLICT, ex.getMessage(), req, null);
	}

	@ExceptionHandler({ InvalidCredentialsException.class, BadCredentialsException.class })
	public ResponseEntity<ApiError> handleBadCredentials(RuntimeException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid email or password", req, null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
	    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req, null);
	}

    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest req,
            List<ApiError.FieldError> fieldErrors) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), message, req.getRequestURI(), fieldErrors));
	}
}