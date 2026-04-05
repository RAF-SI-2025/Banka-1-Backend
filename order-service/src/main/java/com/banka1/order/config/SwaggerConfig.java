package com.banka1.order.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) configuration for the Order Service.
 * Registers the JWT Bearer security scheme so protected endpoints
 * can be tested directly from the Swagger UI.
 */
@Configuration
public class SwaggerConfig {

    /**
     * Builds the OpenAPI specification with service metadata and Bearer authentication.
     *
     * @return configured {@link OpenAPI} instance
     */
    @Bean
    public OpenAPI orderOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .description("Servis za upravljanje aktuarima, nalozima, portfoliom i porezom.")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuthentication"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuthentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
