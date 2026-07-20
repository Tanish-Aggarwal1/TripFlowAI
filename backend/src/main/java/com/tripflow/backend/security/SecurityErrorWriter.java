package com.tripflow.backend.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.exception.ApiError;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes a canonical ApiError JSON body directly to the servlet response.
 * Used by JsonAuthenticationEntryPoint and JsonAccessDeniedHandler, which operate
 * at the security-filter level — before Spring MVC's DispatcherServlet — so they
 * can't return a ResponseEntity the way GlobalExceptionHandler does.
 */
final class SecurityErrorWriter {

	private SecurityErrorWriter() {}

	static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
			String message, String path) throws IOException {
		ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, path,
				List.<ApiError.FieldError>of());

		response.setStatus(status.value());
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}
}