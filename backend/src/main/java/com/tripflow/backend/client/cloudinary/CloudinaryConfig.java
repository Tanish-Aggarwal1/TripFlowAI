package com.tripflow.backend.client.cloudinary;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dedicated configuration class that registers {@link CloudinaryProperties}
 * and the {@link Clock} used by {@link CloudinarySigningService} for
 * deterministic, testable timestamps.
 * <p>
 * Kept separate from {@code BackendApplication} so {@code @WebMvcTest} slices
 * don't eagerly instantiate Cloudinary properties beans.
 * Mirrors the {@code OrsClientConfig} pattern.
 */
@Configuration
@EnableConfigurationProperties(CloudinaryProperties.class)
public class CloudinaryConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}