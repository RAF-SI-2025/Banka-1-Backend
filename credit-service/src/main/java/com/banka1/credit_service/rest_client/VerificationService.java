package com.banka1.credit_service.rest_client;

import com.banka1.credit_service.dto.response.VerificationStatusResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class VerificationService {

    private final RestClient restClient;

    public VerificationService(@Qualifier("verificationClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public VerificationStatusResponse getStatus(Long sessionId) {
        return restClient.get()
                .uri("/{sessionId}/status", sessionId)
                .retrieve()
                .body(VerificationStatusResponse.class);
    }
}
