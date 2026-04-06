package com.banka1.stock_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the external stock market data provider.
 *
 * @param baseUrl base URL of the external market data API
 * @param apiKey API key used to access the provider; it may be blank during the bootstrap phase
 * @param defaultExchange default exchange code for future stock queries
 */
@Validated
@ConfigurationProperties(prefix = "stock.market-data")
public record StockMarketDataProperties(
    @NotBlank String baseUrl,
    String apiKey,
    @NotBlank String defaultExchange
) {
}
