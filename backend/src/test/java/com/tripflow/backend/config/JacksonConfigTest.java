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
}