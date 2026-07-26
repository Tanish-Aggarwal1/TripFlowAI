package com.tripflow.backend.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tripflow.backend.exception.ApiError;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tripFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TripFlowAI API")
                        .description("AI-powered multi-stop trip planning API — trips, stops, "
                                + "route optimization, and Gemini-assisted itinerary suggestions.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    /**
     * ApiError (docs/api-contracts.md) is only ever produced by GlobalExceptionHandler,
     * never a controller return type, so springdoc never discovers it on its own.
     * Register it explicitly so it appears in the Swagger UI schema list.
     */
    @Bean
    public OpenApiCustomizer apiErrorSchemaCustomizer() {
        return openApi -> {
            ResolvedSchema resolved = ModelConverters.getInstance()
                    .resolveAsResolvedSchema(new AnnotatedType(ApiError.class));
            openApi.getComponents().addSchemas("ApiError", resolved.schema);
            resolved.referencedSchemas.forEach(openApi.getComponents()::addSchemas);
        };
    }
}
