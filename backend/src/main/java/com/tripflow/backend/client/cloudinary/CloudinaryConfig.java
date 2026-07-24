package com.tripflow.backend.client.cloudinary;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Dedicated configuration class that registers {@link CloudinaryProperties}.
 * <p>
 * Kept separate from {@code BackendApplication} so {@code @WebMvcTest} slices
 * don't eagerly instantiate Cloudinary properties beans.
 * Mirrors the {@code OrsClientConfig} pattern.
 */
@Configuration
@EnableConfigurationProperties(CloudinaryProperties.class)
public class CloudinaryConfig {
}