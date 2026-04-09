package com.banka1.order.client.impl;

import com.banka1.order.client.ClientClient;
import com.banka1.order.dto.CustomerDto;
import com.banka1.order.dto.CustomerPageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * RestClient-based implementation of {@link ClientClient}.
 * Active in all profiles except "local".
 */
@Component
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class ClientClientImpl implements ClientClient {

    private final RestClient clientRestClient;

    /**
     * {@inheritDoc}
     */
    @Override
    public CustomerDto getCustomer(Long id) {
        return clientRestClient.get()
                .uri("/customers/{id}", id)
                .retrieve()
                .body(CustomerDto.class);
    }

    @Override
    public CustomerPageResponse searchCustomers(String ime, String prezime, int page, int size) {
        return clientRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/customers")
                        .queryParamIfPresent("ime", Optional.ofNullable(ime))
                        .queryParamIfPresent("prezime", Optional.ofNullable(prezime))
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .retrieve()
                .body(CustomerPageResponse.class);
    }
}
