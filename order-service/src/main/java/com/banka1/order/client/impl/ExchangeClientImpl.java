package com.banka1.order.client.impl;

import com.banka1.order.client.ExchangeClient;
import com.banka1.order.dto.ExchangeRateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * RestClient-based implementation of {@link ExchangeClient}.
 * Active in all profiles except "local".
 */
@Component
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class ExchangeClientImpl implements ExchangeClient {

    private final RestClient exchangeRestClient;

    /**
     * {@inheritDoc}
     */
    @Override
    public ExchangeRateDto calculate(String fromCurrency, String toCurrency, BigDecimal amount) {
        return exchangeRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/calculate")
                        .queryParam("fromCurrency", fromCurrency)
                        .queryParam("toCurrency", toCurrency)
                        .queryParam("amount", amount)
                        .build())
                .retrieve()
                .body(ExchangeRateDto.class);
    }

    @Override
    public ExchangeRateDto calculateWithoutCommission(String fromCurrency, String toCurrency, BigDecimal amount) {
        return exchangeRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/calculate/no-commission")
                        .queryParam("fromCurrency", fromCurrency)
                        .queryParam("toCurrency", toCurrency)
                        .queryParam("amount", amount)
                        .build())
                .retrieve()
                .body(ExchangeRateDto.class);
    }
}
