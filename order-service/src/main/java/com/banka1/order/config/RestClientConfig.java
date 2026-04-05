package com.banka1.order.config;

import com.banka1.order.security.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;

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

    /**
     * Interceptor that injects an {@code Authorization: Bearer <token>} header
     * into every outgoing HTTP request using a freshly generated service JWT.
     */
    class JwtAuthInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            String token = jwtService.generateJwtToken();
            request.getHeaders().set("Authorization", "Bearer " + token);
            return execution.execute(request, body);
        }
    }

    /**
     * Shared {@link RestClient.Builder} preconfigured with the JWT interceptor.
     * All service-specific clients are built from this builder.
     *
     * @return a builder with the JWT interceptor attached
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestInterceptor(new JwtAuthInterceptor());
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
