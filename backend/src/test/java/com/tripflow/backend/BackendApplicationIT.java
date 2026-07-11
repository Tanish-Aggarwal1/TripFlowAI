package com.tripflow.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

@SpringBootTest
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
class BackendApplicationIT {

	@Test
	void contextLoads() {
	}
}
