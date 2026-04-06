package com.banka1.stock_service.client;

import com.banka1.stock_service.dto.ExchangeServiceInfoResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Adapter used to call {@code exchange-service} endpoints from the stock module.
 * At the moment it provides the minimal bootstrap integration via the {@code /info} call.
 */
@Component
public class ExchangeServiceClient {

    private final RestClient exchangeServiceRestClient;

    /**
     * @param exchangeServiceRestClient RestClient bean configured for the exchange service
     */
    public ExchangeServiceClient(@Qualifier("exchangeServiceRestClient") RestClient exchangeServiceRestClient) {
        this.exchangeServiceRestClient = exchangeServiceRestClient;
    }

    /**
     * Calls the {@code exchange-service} info endpoint and returns its response.
     *
     * @return deserialized exchange service response
     * @throws ResponseStatusException if the exchange service is unavailable or the HTTP client request fails
     */
    public ExchangeServiceInfoResponse getInfo() {
        try {
            return exchangeServiceRestClient.get()
                    .uri("/info")
                    .retrieve()
                    .body(ExchangeServiceInfoResponse.class);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "exchange-service is unavailable", exception);
        }
    }
}
