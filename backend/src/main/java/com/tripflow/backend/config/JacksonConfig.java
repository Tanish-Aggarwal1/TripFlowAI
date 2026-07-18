package com.tripflow.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Provides a Spring-managed bean for the legacy Jackson 2 {@link ObjectMapper}.
 *
 * <p>Spring Boot 4.1's Jackson autoconfiguration (via spring-boot-starter-webmvc)
 * registers Jackson 3's {@code tools.jackson.databind.json.JsonMapper} bean, not
 * this type. {@code jackson-databind} (Jackson 2) is still on the classpath
 * transitively via {@code jjwt-jackson}, but with no Spring-managed bean of this
 * type — anything needing it (e.g. RouteOptimizationService serializing ORS route
 * geometry to JSON) must get it from here.
 */
@Configuration
public class JacksonConfig {

	 @Bean
	    public ObjectMapper objectMapper() {
	        return new ObjectMapper();
	    }
}
