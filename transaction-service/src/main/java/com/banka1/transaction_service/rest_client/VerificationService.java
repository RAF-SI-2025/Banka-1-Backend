package com.banka1.transaction_service.rest_client;

import com.banka1.transaction_service.dto.response.VerificationStatusResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * REST client for interacting with the Verification Service.
 * Provides methods for verifying user actions.
 */
@Service
public class VerificationService {

    /** REST client with JWT authentication */
    private final RestClient restClient;

    /**
     * Constructor that injects the REST client for the Verification Service.
     *
     * @param restClient configured REST client
     */
    public VerificationService(@Qualifier("verificationClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Verifies a user action based on the provided verification ID.
     *
     * @param sessionId the ID of the verification session
     * @return the verification status response
     */
    public VerificationStatusResponse getStatus(Long sessionId) {
        return restClient.get()
                .uri("/{sessionId}/status", sessionId)
                .retrieve()
                .body(VerificationStatusResponse.class);
    }
}
