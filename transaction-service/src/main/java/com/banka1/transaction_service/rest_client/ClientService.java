package com.banka1.transaction_service.rest_client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * REST client for interacting with the Client Service.
 * Provides methods for retrieving client information.
 */
@Service
public class ClientService {

    /** REST client with JWT authentication */
    private final RestClient restClient;

    /**
     * Constructor that injects the REST client for the User/Client Service.
     *
     * @param restClient configured REST client
     */
    public ClientService(@Qualifier("userClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // TODO: Add methods for finding users by JMBG and ID

}
