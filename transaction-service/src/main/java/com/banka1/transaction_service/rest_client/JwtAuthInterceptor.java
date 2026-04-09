package com.banka1.transaction_service.rest_client;

import com.banka1.transaction_service.security.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;


/**
 * Interceptor for adding JWT authentication headers to outgoing requests.
 * Ensures that requests to external services include the necessary JWT token.
 */
@Configuration
@RequiredArgsConstructor
public class JwtAuthInterceptor implements ClientHttpRequestInterceptor {

    /** Service for generating JWT tokens */
    private final JWTService jwtProvider;

    /**
     * Intercepts the outgoing HTTP request and adds the Authorization header.
     *
     * @param request the HTTP request
     * @param body the request body
     * @param execution the request execution
     * @return the response from the executed request
     * @throws IOException if an I/O error occurs
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {

        String token = jwtProvider.generateJwtToken();
        request.getHeaders().set("Authorization", "Bearer " + token);
        return execution.execute(request, body);
    }
}
