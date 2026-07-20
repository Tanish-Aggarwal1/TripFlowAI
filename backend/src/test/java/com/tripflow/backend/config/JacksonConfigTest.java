package com.tripflow.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(classes = JacksonConfig.class)
class JacksonConfigTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void objectMapperBeanIsRegistered() {
		assertThat(objectMapper).isNotNull();
	}

	@Test
	void objectMapperSerializesSimpleObject() throws Exception {
		String json = objectMapper.writeValueAsString(java.util.Map.of("k", "v"));

		assertThat(json).isEqualTo("{\"k\":\"v\"}");
	}
	
	@Test
	void objectMapperSerializesInstantAsIsoString() throws Exception {
		// Regression test for the InvalidDefinitionException found while building
		// SCRUM-100: this bean previously had no java.time support at all.
		String json = objectMapper.writeValueAsString(java.time.Instant.parse("2026-07-19T21:29:59.437Z"));

		assertThat(json).isEqualTo("\"2026-07-19T21:29:59.437Z\"");
	}
}