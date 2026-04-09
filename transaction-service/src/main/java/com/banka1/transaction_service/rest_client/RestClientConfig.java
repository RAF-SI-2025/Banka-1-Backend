package com.banka1.transaction_service.rest_client;

import com.banka1.transaction_service.security.JWTService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration class for REST clients.
 * Provides beans for configuring REST templates and interceptors.
 */
@Configuration
public class RestClientConfig {

    /**
     * Creates a bean for the RestTemplate with JWT authentication interceptor.
     *
     * @return RestClient builder
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Creates a REST client for the User/Client Service with JWT authentication.
     *
     * @param builder RestClient builder
     * @param baseUrl Base service URL from configuration
     * @param jwtService Service for generating JWT tokens
     * @return configured REST client
     */
    @Bean
    public RestClient userClient(
            RestClient.Builder builder,
            @Value("${services.user.url}") String baseUrl,
            JWTService jwtService
    ) {
        return builder
                .baseUrl(baseUrl)
                .requestInterceptor(new JwtAuthInterceptor(jwtService))
                .build();
    }

    /**
     * Creates a REST client for the Verification Service with JWT authentication.
     *
     * @param builder RestClient builder
     * @param baseUrl Base service URL from configuration
     * @param jwtService Service for generating JWT tokens
     * @return configured REST client
     */
    @Bean
    public RestClient verificationClient(
            RestClient.Builder builder,
            @Value("${services.verification.url}") String baseUrl,
            JWTService jwtService
    ) {
        return builder
                .baseUrl(baseUrl)
                .requestInterceptor(new JwtAuthInterceptor(jwtService))
                .build();
    }

    /**
     * Creates a REST client for the Exchange Service with JWT authentication.
     *
     * @param builder RestClient builder
     * @param baseUrl Base service URL from configuration
     * @param jwtService Service for generating JWT tokens
     * @return configured REST client
     */
    @Bean
    public RestClient exchangeClient(
            RestClient.Builder builder,
            @Value("${services.exchange.url}") String baseUrl,
            JWTService jwtService
    ) {
        return builder
                .baseUrl(baseUrl)
                .requestInterceptor(new JwtAuthInterceptor(jwtService))
                .build();
    }

    /**
     * Creates a REST client for the Account Service with JWT authentication.
     *
     * @param builder RestClient builder
     * @param baseUrl Base service URL from configuration
     * @param jwtService Service for generating JWT tokens
     * @return configured REST client
     */
    @Bean
    public RestClient accountClient(
            RestClient.Builder builder,
            @Value("${services.account.url}") String baseUrl,
            JWTService jwtService
    ) {
        return builder
                .baseUrl(baseUrl)
                .requestInterceptor(new JwtAuthInterceptor(jwtService))
                .build();
    }


}
