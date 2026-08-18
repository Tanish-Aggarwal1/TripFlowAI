package com.tripflow.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link RefreshTokenProperties}. Standalone rather than on BackendApplication for
 * the same reason as {@code JwtConfig} — see that class's javadoc. */
@Configuration
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenConfig {

}
