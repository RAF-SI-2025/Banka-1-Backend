package com.banka1.order.client.impl;

import com.banka1.order.client.AccountClient;
import com.banka1.order.dto.AccountDetailsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * RestClient-based implementation of {@link AccountClient}.
 * Active in all profiles except "local".
 */
@Component
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class AccountClientImpl implements AccountClient {

    private final RestClient accountRestClient;

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountDetailsDto getAccountDetails(String accountNumber) {
        return accountRestClient.get()
                .uri("/internal/accounts/{accountNumber}/details", accountNumber)
                .retrieve()
                .body(AccountDetailsDto.class);
    }
}
