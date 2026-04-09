package com.banka1.credit_service.rest_client;

import com.banka1.credit_service.security.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;


@Configuration
@RequiredArgsConstructor
public class JwtAuthInterceptor implements ClientHttpRequestInterceptor {

    private final JWTService jwtProvider;
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {

        String token = jwtProvider.generateJwtToken();
        request.getHeaders().set("Authorization", "Bearer " + token);
        return execution.execute(request, body);
    }
}
