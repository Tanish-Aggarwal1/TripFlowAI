package com.tripflow.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.tripflow.backend.client.cloudinary.CloudinaryProperties;
import com.tripflow.backend.client.cloudinary.CloudinaryConfig;
import com.tripflow.backend.client.ors.OrsProperties;
import com.tripflow.backend.client.ors.OrsClientConfig;
import com.tripflow.backend.ratelimit.RateLimitProperties;
import com.tripflow.backend.ratelimit.RateLimitConfig;
import com.tripflow.backend.schedule.RouteScheduleProperties;
import com.tripflow.backend.schedule.RouteScheduleConfig;

/**
 * SCRUM-484 regression: OrsProperties/RateLimitProperties/RouteScheduleProperties
 * used to bind a missing property to null silently instead of failing at boot. Proves
 * the added @Validated constraints now reject an incomplete binding with a
 * BindValidationException, matching GeminiProperties/CloudinaryProperties' existing
 * behavior.
 */
class ConfigurationPropertiesValidationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

	@Test
	void orsProperties_missingConnectTimeout_failsToStart() {
		contextRunner.withUserConfiguration(OrsClientConfig.class, RestClientBuilderConfig.class)
				.withPropertyValues("ors.base-url=https://ors.test", "ors.api-key=key", "ors.read-timeout=15s")
				.run(context -> assertThat(context).getFailure()
						.rootCause().isInstanceOf(BindValidationException.class));
	}

	@Test
	void orsProperties_allPropertiesPresent_binds() {
		contextRunner.withUserConfiguration(OrsClientConfig.class, RestClientBuilderConfig.class)
				.withPropertyValues("ors.base-url=https://ors.test", "ors.api-key=key", "ors.connect-timeout=5s",
						"ors.read-timeout=15s")
				.run(context -> assertThat(context).hasSingleBean(OrsProperties.class));
	}

	@Test
	void rateLimitProperties_missingLimit_failsToStart() {
		contextRunner.withUserConfiguration(RateLimitConfig.class)
				.withPropertyValues("app.ratelimit.ai-suggest.capacity=5", "app.ratelimit.ai-suggest.window=1h",
						"app.ratelimit.optimize.capacity=5", "app.ratelimit.optimize.window=1h",
						"app.ratelimit.ai-generate.capacity=5", "app.ratelimit.ai-generate.window=1h",
						"app.ratelimit.login.capacity=5", "app.ratelimit.login.window=1h",
						"app.ratelimit.register.capacity=5", "app.ratelimit.register.window=1h")
				// app.ratelimit.refresh.* deliberately omitted
				.run(context -> assertThat(context).getFailure()
						.rootCause().isInstanceOf(BindValidationException.class));
	}

	@Test
	void routeScheduleProperties_missingDayEndTime_failsToStart() {
		contextRunner.withUserConfiguration(RouteScheduleConfig.class)
				.withPropertyValues("app.schedule.day-start-time=09:00", "app.schedule.default-visit-duration=1h")
				// app.schedule.day-end-time deliberately omitted
				.run(context -> assertThat(context).getFailure()
						.rootCause().isInstanceOf(BindValidationException.class));
	}

	@Test
	void routeScheduleProperties_allPropertiesPresent_binds() {
		contextRunner.withUserConfiguration(RouteScheduleConfig.class)
				.withPropertyValues("app.schedule.day-start-time=09:00", "app.schedule.day-end-time=21:00",
						"app.schedule.default-visit-duration=1h")
				.run(context -> assertThat(context).hasSingleBean(RouteScheduleProperties.class));
	}

	@Test
	void cloudinaryProperties_placeholderWithNoDefaultAndEnvVarUnset_failsToStart() {
		// SCRUM-461: application.properties binds these via ${CLOUDINARY_CLOUD_NAME} etc.
		// with no fallback default. @NotBlank alone does NOT catch this — Spring's Binder
		// leaves an unresolvable placeholder as the literal, non-blank text (verified via
		// CloudinaryProperties' own requireResolved guard, added for exactly this reason).
		contextRunner.withUserConfiguration(CloudinaryConfig.class)
				.withPropertyValues("app.cloudinary.cloud-name=${CLOUDINARY_CLOUD_NAME}",
						"app.cloudinary.api-key=${CLOUDINARY_API_KEY}",
						"app.cloudinary.api-secret=${CLOUDINARY_API_SECRET}")
				.run(context -> assertThat(context).getFailure()
						.rootCause().isInstanceOf(IllegalStateException.class)
						.hasMessageContaining("unresolved placeholder"));
	}

	@Test
	void cloudinaryProperties_allPropertiesPresent_binds() {
		contextRunner.withUserConfiguration(CloudinaryConfig.class)
				.withPropertyValues("app.cloudinary.cloud-name=my-cloud", "app.cloudinary.api-key=key",
						"app.cloudinary.api-secret=secret")
				.run(context -> assertThat(context).hasSingleBean(CloudinaryProperties.class));
	}

	@Configuration
	static class RestClientBuilderConfig {
		@Bean
		RestClient.Builder restClientBuilder() {
			return RestClient.builder();
		}
	}
}
