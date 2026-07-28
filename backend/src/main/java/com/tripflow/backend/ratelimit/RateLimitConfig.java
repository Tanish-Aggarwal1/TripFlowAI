package com.tripflow.backend.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers RateLimitProperties. Standalone @Configuration class (mirroring
 * JwtConfig/OrsClientConfig) so narrow test slices that don't touch rate limiting
 * don't eagerly pull it in.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

}
