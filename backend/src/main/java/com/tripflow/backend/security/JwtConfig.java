package com.tripflow.backend.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers JwtProperties. Kept as its own @Configuration class (mirroring
 * OrsClientConfig/OrsProperties) rather than on BackendApplication directly —
 * @WebMvcTest and other narrow slices use the detected @SpringBootConfiguration
 * class as their base config source, so anything declared directly on
 * BackendApplication gets eagerly created in every slice, even ones that mock
 * JwtService away entirely (see AuthControllerTest). A standalone @Configuration
 * class here is excluded from those slices unless something in them actually
 * needs it.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

}
