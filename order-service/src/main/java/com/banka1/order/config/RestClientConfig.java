package com.banka1.order.config;

import com.banka1.order.security.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Configuration for {@link RestClient} instances used to communicate with external microservices.
 * All clients share a common JWT interceptor that attaches a service-level Bearer token to every request.
 * Active in all profiles except "local".
 */
@Configuration
@RequiredArgsConstructor
@Profile("!local")
public class RestClientConfig {

    private final JWTService jwtService;

    @Value("${banka.security.expiration-time:3600000}")
    private long tokenValidityMillis;

    @Bean
    public ClientHttpRequestInterceptor jwtAuthInterceptor() {
        return new ServiceJwtAuthInterceptor(jwtService, tokenValidityMillis);
    }

    /**
     * Shared {@link RestClient.Builder} preconfigured with the JWT interceptor.
     * All service-specific clients are built from this builder.
     *
     * @return a builder with the JWT interceptor attached
     */
    @Bean
    public RestClient.Builder restClientBuilder(ClientHttpRequestInterceptor jwtAuthInterceptor) {
        return RestClient.builder()
                .requestInterceptor(jwtAuthInterceptor);
    }

    /**
     * RestClient for account-service communication.
     *
     * @param builder  shared builder from {@link #restClientBuilder()}
     * @param baseUrl  resolved from {@code services.account.url} property
     */
    @Bean
    public RestClient accountRestClient(RestClient.Builder builder, @Value("${services.account.url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    /**
     * RestClient for employee-service communication.
     *
     * @param builder  shared builder from {@link #restClientBuilder()}
     * @param baseUrl  resolved from {@code services.employee.url} property
     */
    @Bean
    public RestClient employeeRestClient(RestClient.Builder builder, @Value("${services.employee.url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    /**
     * RestClient for client-service communication.
     *
     * @param builder  shared builder from {@link #restClientBuilder()}
     * @param baseUrl  resolved from {@code services.client.url} property
     */
    @Bean
    public RestClient clientRestClient(RestClient.Builder builder, @Value("${services.client.url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    /**
     * RestClient for exchange-service communication.
     *
     * @param builder  shared builder from {@link #restClientBuilder()}
     * @param baseUrl  resolved from {@code services.exchange.url} property
     */
    @Bean
    public RestClient exchangeRestClient(RestClient.Builder builder, @Value("${services.exchange.url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    /**
     * RestClient for stock-service communication.
     *
     * @param builder  shared builder from {@link #restClientBuilder()}
     * @param baseUrl  resolved from {@code services.stock.url} property
     */
    @Bean
    public RestClient stockRestClient(RestClient.Builder builder, @Value("${services.stock.url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
