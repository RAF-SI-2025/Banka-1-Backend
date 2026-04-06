package com.banka1.stock_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for internal communication with {@code exchange-service}.
 *
 * @param baseUrl base URL of the exchange service used by the {@code RestClient}
 */
@Validated
@ConfigurationProperties(prefix = "stock.exchange-service")
public record ExchangeServiceClientProperties(
        @NotBlank String baseUrl
) {
}
