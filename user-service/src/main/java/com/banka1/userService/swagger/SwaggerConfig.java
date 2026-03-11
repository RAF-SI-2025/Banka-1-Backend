package com.banka1.userService.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String APP_TITLE = "User Service API";
    private static final String APP_DESCRIPTION = "API for user and authentication management";
    private static final String APP_VERSION = "1.0";

    /**
     * Konfigurise OpenAPI specifikaciju za servis i bearer autentikaciju.
     *
     * @return OpenAPI opis servisa
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .info(new Info()
                        .title(APP_TITLE)
                        .description(APP_DESCRIPTION)
                        .version(APP_VERSION));
    }
}

