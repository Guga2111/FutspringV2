package com.futspring.backend.shared.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document (springdoc), served only when API_DOCS_ENABLED=true. Every route takes the Bearer JWT, so
 * Swagger UI can call protected routes after "Authorize" with a token from /api/v1/auth/login.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Futspring API", version = "v1"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER))
@SecurityScheme(name = OpenApiConfig.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    static final String BEARER = "bearer";
}
