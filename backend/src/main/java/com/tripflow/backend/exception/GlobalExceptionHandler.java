package com.tripflow.backend.exception;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
		log.warn("404 Not Found on {}: {}", req.getRequestURI(), ex.getMessage());
		return error(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex, HttpServletRequest req) {
		log.warn("403 Forbidden on {}: {}", req.getRequestURI(), ex.getMessage());
		return error(HttpStatus.FORBIDDEN, ex.getMessage(), req, null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {

		List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage())).toList();
		log.warn("400 Bad Request on {}: {} field error(s)", req.getRequestURI(), fieldErrors.size());
		return error(HttpStatus.BAD_REQUEST, "Validation failed", req, fieldErrors);
	}

	@ExceptionHandler({ DuplicateEmailException.class, DuplicateUsernameException.class, ConflictException.class })
	public ResponseEntity<ApiError> handleDuplicate(RuntimeException ex, HttpServletRequest req) {
		log.warn("409 Conflict on {}: {}", req.getRequestURI(), ex.getMessage());
		return error(HttpStatus.CONFLICT, ex.getMessage(), req, null);
	}

	@ExceptionHandler({ InvalidCredentialsException.class, BadCredentialsException.class })
	public ResponseEntity<ApiError> handleBadCredentials(RuntimeException ex, HttpServletRequest req) {
		// Message intentionally generic to the client; log at warn without echoing the submitted credentials.
        log.warn("401 Unauthorized on {}: invalid credentials", req.getRequestURI());
        return error(HttpStatus.UNAUTHORIZED, "Invalid email or password", req, null);
	}

	@ExceptionHandler(InsufficientStopsException.class)
	public ResponseEntity<ApiError> handleInsufficientStops(InsufficientStopsException ex, HttpServletRequest req) {
		log.warn("422 Unprocessable Entity on {}: {}", req.getRequestURI(), ex.getMessage());
		return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req, null);
	}

	@ExceptionHandler(OrsClientException.class)
	public ResponseEntity<ApiError> handleOrsFailure(OrsClientException ex, HttpServletRequest req) {
	    log.error("502 Bad Gateway on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
	    return error(HttpStatus.BAD_GATEWAY, "Route service is temporarily unavailable", req, null);
	}
	
	@ExceptionHandler(GeminiClientException.class)
	public ResponseEntity<ApiError> handleGeminiFailure(GeminiClientException ex, HttpServletRequest req) {
	    log.error("502 Bad Gateway on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
	    return error(HttpStatus.BAD_GATEWAY, "AI itinerary service is temporarily unavailable", req, null);
	}
	
	@ExceptionHandler(OrsRateLimitException.class)
	public ResponseEntity<ApiError> handleOrsRateLimit(OrsRateLimitException ex, HttpServletRequest req) {
		log.warn("429 Too Many Requests on {}: {}", req.getRequestURI(), ex.getMessage());
		return error(HttpStatus.TOO_MANY_REQUESTS,
				"Route optimization is rate-limited, please try again shortly", req, null);
	}
	
	@ExceptionHandler(GeminiParsingException.class)
	public ResponseEntity<ApiError> handleGeminiParsing(GeminiParsingException ex, HttpServletRequest req) {
	    log.error("502 Bad Gateway on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
	    return error(HttpStatus.BAD_GATEWAY, "AI itinerary service returned an unreadable response", req, null);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
		// Don't echo ex.getMessage() to the client — it can contain fragments of the
		// submitted payload. Log it server-side only.
		log.warn("400 Bad Request on {}: malformed request body: {}", req.getRequestURI(), ex.getMessage());
		return error(HttpStatus.BAD_REQUEST, "Malformed request body", req, null);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
		String message = "Invalid value for parameter '" + ex.getName() + "'";
		log.warn("400 Bad Request on {}: {}", req.getRequestURI(), message);
		return error(HttpStatus.BAD_REQUEST, message, req, null);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex,
			HttpServletRequest req) {
		// Generic message only — constraint/table names and SQL fragments are schema
		// information disclosure. Full detail stays server-side at WARN.
		log.warn("409 Conflict on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
		return error(HttpStatus.CONFLICT, "The request conflicts with existing data", req, null);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest req) {
		log.warn("405 Method Not Allowed on {}: {}", req.getRequestURI(), ex.getMessage());
		return error(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), req, null);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest req) {
		log.warn("404 Not Found on {}: no matching route", req.getRequestURI());
		return error(HttpStatus.NOT_FOUND, "No matching route for this request", req, null);
	}

	@ExceptionHandler(PromptTooLargeException.class)
	public ResponseEntity<ApiError> handlePromptTooLarge(PromptTooLargeException ex, HttpServletRequest req) {
		log.warn("400 Bad Request on {}: {}", req.getRequestURI(), ex.getMessage());
		return error(HttpStatus.BAD_REQUEST, "Itinerary preferences produced too large a request", req, null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
		log.error("500 Internal Server Error on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
	    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req, null);
	}

    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest req,
            List<ApiError.FieldError> fieldErrors) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), message, req.getRequestURI(), fieldErrors));
	}
}