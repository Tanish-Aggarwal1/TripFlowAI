package com.tripflow.backend.client.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(classes = { GeminiClientConfig.class, GeminiClientConfigTest.RestClientBuilderTestConfig.class })
@ActiveProfiles("test")
public class GeminiClientConfigTest {
	
	@Autowired
	private GeminiProperties geminiProperties;

	@Autowired
	private RestClient geminiRestClient;

	@Test
	void propertiesBindFromApplicationProperties() {
		assertThat(geminiProperties.baseUrl()).isEqualTo("https://gemini.test");
		assertThat(geminiProperties.apiKey()).isEqualTo("test-placeholder");
		assertThat(geminiProperties.model()).isEqualTo("gemini-test-model");
		assertThat(geminiProperties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
		assertThat(geminiProperties.readTimeout()).isEqualTo(Duration.ofSeconds(20));
	}

	@Test
	void geminiRestClientBeanIsRegistered() {
		assertThat(geminiRestClient).isNotNull();
	}

	@Test
	void toString_masksApiKey() {
		assertThat(geminiProperties.toString()).doesNotContain("test-placeholder");
	}

	@TestConfiguration
	static class RestClientBuilderTestConfig {
		@Bean
		RestClient.Builder restClientBuilder() {
			return RestClient.builder();
		}
	}

}
