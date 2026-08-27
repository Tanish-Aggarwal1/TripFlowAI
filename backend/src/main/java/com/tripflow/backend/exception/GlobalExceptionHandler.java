package com.tripflow.backend.exception;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
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

import com.tripflow.backend.ratelimit.RateLimitExceededException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
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

	@ExceptionHandler(InvalidRequestException.class)
	public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest req) {
		log.warn("400 Bad Request on {}: {}", req.getRequestURI(), ex.getMessage());
		return error(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {

		List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage())).toList();
		log.warn("400 Bad Request on {}: {} field error(s)", req.getRequestURI(), fieldErrors.size());
		return error(HttpStatus.BAD_REQUEST, "Validation failed", req, fieldErrors);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
		List<ApiError.FieldError> fieldErrors = ex.getConstraintViolations().stream()
				.map(cv -> new ApiError.FieldError(lastPathSegment(cv.getPropertyPath().toString()), cv.getMessage()))
				.toList();
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

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException ex, HttpServletRequest req) {
		// No token material in the log line — the path is enough to locate the call.
		log.warn("401 Unauthorized on {}: invalid refresh token", req.getRequestURI());
		return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), req, null);
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

	@ExceptionHandler(MapboxClientException.class)
	public ResponseEntity<ApiError> handleMapboxFailure(MapboxClientException ex, HttpServletRequest req) {
	    log.error("502 Bad Gateway on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
	    return error(HttpStatus.BAD_GATEWAY, "Map snapshot service is temporarily unavailable", req, null);
	}

	@ExceptionHandler(OrsRateLimitException.class)
	public ResponseEntity<ApiError> handleOrsRateLimit(OrsRateLimitException ex, HttpServletRequest req) {
		log.warn("429 Too Many Requests on {}: {}", req.getRequestURI(), ex.getMessage());
		return error(HttpStatus.TOO_MANY_REQUESTS,
				"Route optimization is rate-limited, please try again shortly", req, null);
	}

	@ExceptionHandler(GeminiRateLimitException.class)
	public ResponseEntity<ApiError> handleGeminiRateLimit(GeminiRateLimitException ex, HttpServletRequest req) {
		log.warn("429 Too Many Requests on {}: {}", req.getRequestURI(), ex.getMessage());
		return error(HttpStatus.TOO_MANY_REQUESTS,
				"AI itinerary service is rate-limited, please try again shortly", req, null);
	}

	@ExceptionHandler(GeminiParsingException.class)
	public ResponseEntity<ApiError> handleGeminiParsing(GeminiParsingException ex, HttpServletRequest req) {
	    log.error("502 Bad Gateway on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
	    return error(HttpStatus.BAD_GATEWAY, "AI itinerary service returned an unreadable response", req, null);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
		// RequestSizeLimitFilter's wrapped stream throws PayloadTooLargeException mid-read when
		// Content-Length was missing/understated (e.g. chunked encoding) — Jackson surfaces that
		// as an IOException, which Spring wraps here, so unwrap the cause chain to tell a genuinely
		// oversized body apart from an ordinary malformed one.
		if (findCause(ex, PayloadTooLargeException.class) != null) {
			log.warn("413 Payload Too Large on {}: request body exceeded size limit mid-read", req.getRequestURI());
			return error(HttpStatus.PAYLOAD_TOO_LARGE, "Request body exceeds the maximum allowed size", req, null);
		}
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

	@ExceptionHandler(InvalidPhotoUrlException.class)
	public ResponseEntity<ApiError> handleInvalidPhotoUrl(InvalidPhotoUrlException ex, HttpServletRequest req) {
		log.warn("400 Bad Request on {}: {}", req.getRequestURI(), ex.getMessage());
		return error(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
	}

	@ExceptionHandler(RateLimitExceededException.class)
	public ResponseEntity<ApiError> handleRateLimitExceeded(RateLimitExceededException ex, HttpServletRequest req) {
		log.warn("429 Too Many Requests on {}: {}", req.getRequestURI(), ex.getMessage());
		ApiError body = new ApiError(HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
				ex.getMessage(), req.getRequestURI(), null);
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
				.body(body);
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

	// "searchPublicTrips.q" -> "q" — @Validated path parameters carry the method name as a prefix.
	private String lastPathSegment(String propertyPath) {
		int lastDot = propertyPath.lastIndexOf('.');
		return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
	}

	private <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (type.isInstance(t)) {
				return type.cast(t);
			}
		}
		return null;
	}
}