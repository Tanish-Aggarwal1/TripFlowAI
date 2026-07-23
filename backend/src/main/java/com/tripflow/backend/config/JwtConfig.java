package com.tripflow.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers JwtProperties as a bound, validated configuration bean.
 * Kept as its own file (mirrors OrsClientConfig/OrsProperties) so this
 * ticket doesn't need to touch the SecurityConfig.java serialize-point.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
}