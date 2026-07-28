package com.tripflow.backend.schedule;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers RouteScheduleProperties. Standalone @Configuration class (mirroring
 * JwtConfig/RateLimitConfig) so narrow test slices that don't touch scheduling
 * don't eagerly pull it in.
 */
@Configuration
@EnableConfigurationProperties(RouteScheduleProperties.class)
public class RouteScheduleConfig {

}
