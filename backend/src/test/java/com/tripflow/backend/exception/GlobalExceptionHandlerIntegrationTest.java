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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
	void malformedJson_returns400ApiErrorWithoutLeakingRawPayload() throws Exception {
		ResultActions result = mockMvc.perform(post("/test/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{not valid json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("Malformed request body"))
				.andExpect(jsonPath("$.path").value("/test/validation"));

		assertApiErrorKeys(result.andReturn());
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
	void nonNumericPathVariable_returns400ApiError() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/type-mismatch/not-a-number"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("Bad Request"))
				.andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"))
				.andExpect(jsonPath("$.path").value("/test/type-mismatch/not-a-number"));

		assertApiErrorKeys(result.andReturn());
	}

	@Test
	void dataIntegrityViolation_returns409ApiErrorWithoutLeakingSchemaDetails() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/data-integrity-violation"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.message").value("The request conflicts with existing data"))
				.andExpect(jsonPath("$.path").value("/test/data-integrity-violation"));

		MvcResult mvcResult = result.andReturn();
		String body = mvcResult.getResponse().getContentAsString();
		assertThat(body).doesNotContain("users_email_key", "some_table", "SQLState", "constraint");

		assertApiErrorKeys(mvcResult);
	}

	@Test
	void unsupportedHttpMethod_returns405ApiError() throws Exception {
		ResultActions result = mockMvc.perform(post("/test/not-found"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.status").value(405))
				.andExpect(jsonPath("$.error").value("Method Not Allowed"))
				.andExpect(jsonPath("$.path").value("/test/not-found"));

		assertApiErrorKeys(result.andReturn());
	}

	@Test
	void noMatchingRoute_returns404ApiError() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/no-resource"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value("No matching route for this request"))
				.andExpect(jsonPath("$.path").value("/test/no-resource"));

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

	@Test
	void insufficientStops_returns422ApiError() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/insufficient-stops"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.error").value("Unprocessable Entity"))
				.andExpect(jsonPath("$.message").value("Trip needs at least 2 stops to optimize"))
				.andExpect(jsonPath("$.path").value("/test/insufficient-stops"));

		assertApiErrorKeys(result.andReturn());
	}

	@Test
	void orsClientFailure_returns502ApiErrorWithoutLeakingDetails() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/ors-failure"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.status").value(502))
				.andExpect(jsonPath("$.error").value("Bad Gateway"))
				.andExpect(jsonPath("$.message").value("Route service is temporarily unavailable"))
				.andExpect(jsonPath("$.path").value("/test/ors-failure"));

		assertApiErrorKeys(result.andReturn());
	}
	
	@Test
	void orsRateLimit_returns429ApiError() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/ors-rate-limit"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(jsonPath("$.error").value("Too Many Requests"))
				.andExpect(jsonPath("$.path").value("/test/ors-rate-limit"));

		assertApiErrorKeys(result.andReturn());
	}

	// Bonus, not coverage-critical: handleDuplicate and handleBadCredentials each match two
	// exception types via @ExceptionHandler({A.class, B.class}). JaCoCo already counts every
	// line in these methods as covered from the single-type tests above (Spring's exception-
	// to-handler dispatch isn't in-method branching), so these two tests don't move the
	// coverage percentage — they exist purely to confirm the second type in each group
	// actually reaches the same handler at runtime.
	@Test
	void duplicateUsername_returns409ApiError() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/conflict-username"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.message").value("Username already taken: takenuser"))
				.andExpect(jsonPath("$.path").value("/test/conflict-username"));

		assertApiErrorKeys(result.andReturn());
	}

	@Test
	void genericConflict_returns409ApiError() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/conflict-generic"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.message").value("Registration conflicted with an existing account"))
				.andExpect(jsonPath("$.path").value("/test/conflict-generic"));

		assertApiErrorKeys(result.andReturn());
	}

	@Test
	void springSecurityBadCredentials_returns401ApiError() throws Exception {
		ResultActions result = mockMvc.perform(get("/test/unauthorized-spring"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Invalid email or password"))
				.andExpect(jsonPath("$.path").value("/test/unauthorized-spring"));

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

		@GetMapping("/test/type-mismatch/{id}")
		public void typeMismatch(@PathVariable Long id) {
		}

		@GetMapping("/test/data-integrity-violation")
		public void dataIntegrityViolation() {
			throw new DataIntegrityViolationException(
					"ERROR: duplicate key value violates unique constraint \"users_email_key\" "
							+ "on some_table; SQLState: 23505");
		}

		@GetMapping("/test/no-resource")
		public void noResource() throws NoResourceFoundException {
			throw new NoResourceFoundException(HttpMethod.GET, "/test/no-resource", "/test/no-resource");
		}

		@GetMapping("/test/conflict")
		public void conflict() {
			throw new DuplicateEmailException("taken@example.com");
		}

		@GetMapping("/test/server-error")
		public void serverError() {
			throw new IllegalStateException("boom");
		}

		@GetMapping("/test/insufficient-stops")
		public void insufficientStops() {
			throw new InsufficientStopsException("Trip needs at least 2 stops to optimize");
		}

		@GetMapping("/test/ors-failure")
		public void orsFailure() {
			throw new OrsClientException("ORS request failed");
		}
		
		@GetMapping("/test/ors-rate-limit")
		public void orsRateLimit() {
			throw new OrsRateLimitException("ORS rate limited", null);
		}

		@GetMapping("/test/conflict-username")
		public void conflictUsername() {
			throw new DuplicateUsernameException("takenuser");
		}

		@GetMapping("/test/conflict-generic")
		public void conflictGeneric() {
			throw new ConflictException("Registration conflicted with an existing account");
		}

		@GetMapping("/test/unauthorized-spring")
		public void unauthorizedSpring() {
			throw new BadCredentialsException("bad creds");
		}
	}
}