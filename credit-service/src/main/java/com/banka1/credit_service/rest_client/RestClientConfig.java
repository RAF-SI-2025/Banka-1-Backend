package com.banka1.credit_service.rest_client;

import com.banka1.credit_service.security.JWTService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

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
