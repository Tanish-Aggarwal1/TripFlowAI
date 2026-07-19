package com.tripflow.backend.client.ors;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(classes = { OrsClientConfig.class, OrsClientConfigTest.RestClientBuilderTestConfig.class })
@ActiveProfiles("test")
class OrsClientConfigTest {

	@Autowired
	private OrsProperties orsProperties;

	@Autowired
	private RestClient orsRestClient;

	@Test
	void propertiesBindFromApplicationProperties() {
		assertThat(orsProperties.baseUrl()).isEqualTo("https://ors.test");
		assertThat(orsProperties.apiKey()).isEqualTo("test-placeholder");
		assertThat(orsProperties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
		assertThat(orsProperties.readTimeout()).isEqualTo(Duration.ofSeconds(15));
	}

	@Test
	void orsRestClientBeanIsRegistered() {
		assertThat(orsRestClient).isNotNull();
	}

	@TestConfiguration
	static class RestClientBuilderTestConfig {
		@Bean
		RestClient.Builder restClientBuilder() {
			return RestClient.builder();
		}
	}
}
