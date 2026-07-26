package com.tripflow.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

/**
 * REF-23: verifies springdoc actually generates a valid OpenAPI document for this app
 * (all controllers, the ApiError schema) and that Swagger UI is reachable without a JWT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiIT {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocs_unauthenticated_returnsOpenApiDocumentWithAllControllersAndApiErrorSchema() throws Exception {
		mockMvc.perform(get("/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("TripFlowAI API"))
				.andExpect(jsonPath("$.paths./api/auth/login").exists())
				.andExpect(jsonPath("$.paths./api/trips").exists())
				.andExpect(jsonPath("$.paths./api/trips/{id}/optimize").exists())
				.andExpect(jsonPath("$.paths./api/trips/{id}/ai-suggest").exists())
				.andExpect(jsonPath("$.paths./api/trips/{tripId}/stops").exists())
				.andExpect(jsonPath("$.components.schemas.ApiError").exists());
	}

	@Test
	void swaggerUi_unauthenticated_isServed() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(result -> {
					String contentType = result.getResponse().getContentType();
					org.assertj.core.api.Assertions.assertThat(contentType)
							.contains(MediaType.TEXT_HTML_VALUE);
				});
	}
}
