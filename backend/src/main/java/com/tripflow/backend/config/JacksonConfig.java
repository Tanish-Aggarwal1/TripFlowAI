package com.tripflow.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Provides a Spring-managed bean for the legacy Jackson 2 {@link ObjectMapper}.
 *
 * <p>
 * Spring Boot 4.1's Jackson autoconfiguration (via spring-boot-starter-webmvc)
 * registers Jackson 3's {@code tools.jackson.databind.json.JsonMapper} bean,
 * not this type. {@code jackson-databind} (Jackson 2) is still on the classpath
 * transitively via {@code jjwt-jackson}, but with no Spring-managed bean of
 * this type — anything needing it (e.g. RouteOptimizationService serializing
 * ORS route geometry to JSON) must get it from here.
 *
 * <p>
 * JavaTimeModule is registered explicitly — Jackson 2's ObjectMapper does not
 * auto-detect it the way some builders do. WRITE_DATES_AS_TIMESTAMPS is
 * disabled so Instant fields serialize as ISO-8601 strings, matching the
 * autoconfigured Jackson 3 mapper's behavior elsewhere in the app rather than a
 * numeric array.
 * 
 */
@Configuration
public class JacksonConfig {

	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
	}
}
