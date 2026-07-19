package com.tripflow.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class HealthEndpointIT {
	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthEndpoint_unauthenticated_returns200Up() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void envEndpoint_neverExposed() throws Exception {
		// Measured 2026-07-19: 403, confirming WB-01's documented finding — unauthenticated
		// requests currently fall through to Spring Security's default and return 403, not
		// 401. This will become 401 once REF-11 (SCRUM-100) lands its custom entry point;
		// update this assertion then. AC amended from the ticket's original 401/404 guess.
		mockMvc.perform(get("/actuator/env"))
				.andExpect(status().isForbidden());
	}

}