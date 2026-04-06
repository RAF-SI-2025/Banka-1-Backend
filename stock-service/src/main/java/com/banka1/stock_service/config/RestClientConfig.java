package com.banka1.stock_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration of HTTP clients used by {@code stock-service}
 * to communicate with internal and external services.
 */
@Configuration
public class RestClientConfig {

    /**
     * Creates a dedicated {@link RestClient} for {@code exchange-service}.
     *
     * @param properties exchange-service integration configuration
     * @return configured RestClient with the exchange service base URL
     */
    @Bean
    public RestClient exchangeServiceRestClient(ExchangeServiceClientProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    /**
     * Creates a dedicated {@link RestClient} for the external market data provider.
     *
     * @param properties external stock data provider configuration
     * @return configured RestClient with the market data base URL
     */
    @Bean
    public RestClient stockMarketDataRestClient(StockMarketDataProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }
}
