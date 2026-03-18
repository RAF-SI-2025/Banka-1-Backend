package com.banka1.account_service.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    /**
     * Naziv aplikacije koji se prikazuje u Swagger UI.
     */
    private static final String APP_TITLE = "Account service API";

    /**
     * Opis API-ja koji se prikazuje u Swagger UI.
     */
    private static final String APP_DESCRIPTION = "API for accountst";

    /**
     * Verzija API-ja.
     */
    private static final String APP_VERSION = "1.0";

    /**
     * Konfigurise OpenAPI specifikaciju za servis i bearer autentikaciju.
     * Dodaje JWT Bearer security shemu i osnovne informacije o API-ju.
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