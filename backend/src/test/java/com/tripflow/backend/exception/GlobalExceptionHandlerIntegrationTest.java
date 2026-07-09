package com.tripflow.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public class GlobalExceptionHandlerIntegrationTest {

	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}
	
	@Test
	void validationError_returns400ApiErrorWithFieldErrors() throws Exception {
		ResultActions result = mockMvc.perform(post("/test/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.path").value("/test/validation"))
				.andExpect(jsonPath("$.fieldErrors").isArray())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"));

		assertApiErrorKeys(result.andReturn());
	}

	@Test
	void invalidCredentials_returns401ApiError() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/unauthorized"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Invalid email or password"))
				.andExpect(jsonPath("$.path").value("/test/unauthorized"));

		assertApiErrorKeys(result.andReturn());
	}

	@Test
	void forbidden_returns403ApiError() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/forbidden"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.error").value("Forbidden"))
				.andExpect(jsonPath("$.message").value("You do not have access to this trip"))
				.andExpect(jsonPath("$.path").value("/test/forbidden"));

		assertApiErrorKeys(result.andReturn());
	}

	@Test
	void resourceNotFound_returns404ApiError() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value("Trip not found"))
				.andExpect(jsonPath("$.path").value("/test/not-found"));

		assertApiErrorKeys(result.andReturn());
	}

	@Test
	void duplicateEmail_returns409ApiError() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/conflict"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.message").value("Email already registered: taken@example.com"))
				.andExpect(jsonPath("$.path").value("/test/conflict"));

		assertApiErrorKeys(result.andReturn());
	}

	@Test
	void unexpectedException_returns500ApiErrorWithoutLeakingDetails() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/server-error"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.error").value("Internal Server Error"))
				.andExpect(jsonPath("$.message").value("Unexpected error"))
				.andExpect(jsonPath("$.path").value("/test/server-error"));

		assertApiErrorKeys(result.andReturn());
	}

	private void assertApiErrorKeys(MvcResult result) throws Exception {
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

		List<String> keys = new ArrayList<>();
		body.fieldNames().forEachRemaining(keys::add);
		assertThat(keys).containsExactlyInAnyOrder(
				"timestamp", "status", "error", "message", "path", "fieldErrors");

		assertThat(body.get("timestamp").isTextual()).isTrue();
	}

	@RestController
	static class ThrowingController {

		record ValidatedRequest(@NotBlank String name) {
		}

		@PostMapping("/test/validation")
		public void validation(@Valid @RequestBody ValidatedRequest request) {
		}

		@GetMapping("/test/unauthorized")
		public void unauthorized() {
			throw new InvalidCredentialsException();
		}

		@GetMapping("/test/forbidden")
		public void forbidden() {
			throw new ForbiddenException("You do not have access to this trip");
		}

		@GetMapping("/test/not-found")
		public void notFound() {
			throw new ResourceNotFoundException("Trip not found");
		}

		@GetMapping("/test/conflict")
		public void conflict() {
			throw new DuplicateEmailException("taken@example.com");
		}

		@GetMapping("/test/server-error")
		public void serverError() {
			throw new IllegalStateException("boom");
		}
	}
}
