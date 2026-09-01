package com.tripflow.backend.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;
    private final JsonAccessDeniedHandler jsonAccessDeniedHandler;


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Spring Security's CSRF machinery intentionally disabled. This API is stateless
            // (JWT bearer-token auth via the Authorization header, SessionCreationPolicy.STATELESS
            // below) with one exception: an httpOnly `refresh_token` cookie, scoped to
            // Path=/api/auth, consumed only by POST /api/auth/refresh (and, later, logout).
            //
            // Those cookie-consuming endpoints ARE protected against CSRF -- just not by a
            // synchronizer token, and not by SameSite. AuthController requires a non-simple
            // `X-Requested-With` header as the first statement of the refresh handler: a
            // cross-site form or <img> cannot set a custom header, so the browser is forced into
            // a CORS preflight that only an origin in app.cors.allowed-origins survives.
            //
            // SameSite could not carry this: the deployed frontend and backend are different
            // subdomains of a shared PaaS suffix (onrender.com) and are therefore cross-*site*,
            // so Lax/Strict would silently drop the cookie in production while working on
            // localhost. See docs/auth.md "Refresh Tokens".
            //
            // Full synchronizer-token machinery stays unwarranted for two endpoints in a
            // JSON-only API behind an explicit origin allowlist. Flagged by CodeQL
            // (java/spring-disabled-csrf-protection) and dismissed for the reasons above.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
            		// SOCIAL-01 / 06-CONTEXT.md Phase Boundary: the discovery surface (feed,
            		// trips, search) requires authentication — it is not a public unauthenticated
            		// surface. "/api/discovery/**" was removed from this list deliberately; every
            		// DiscoveryController handler now falls through to anyRequest().authenticated().
            		.requestMatchers("/api/auth/**", "/actuator/health", "/actuator/metrics", "/actuator/metrics/**",
            				"/swagger-ui.html", "/swagger-ui/**", "/api-docs", "/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                    .accessDeniedHandler(jsonAccessDeniedHandler))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // X-Requested-With must be allow-listed or the preflight for the refresh endpoint's
        // CSRF gate (see the csrf comment above) fails and the gate becomes unreachable.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        // The refresh token is delivered as an httpOnly cookie, so the deployed frontend origin
        // needs credentialed CORS before the browser will attach it cross-site at all.
        // Do NOT relax setAllowedOrigins (an explicit list bound from app.cors.allowed-origins)
        // to the pattern-accepting variant: patterns permit wildcards alongside credentials,
        // which would open every endpoint to any origin. Spring fails bean creation on a
        // wildcard paired with credentials, and that startup failure is the safety net.
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}