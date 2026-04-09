package com.banka1.order.client.impl;

import com.banka1.order.client.StockClient;
import com.banka1.order.dto.StockExchangeDto;
import com.banka1.order.dto.StockListingDto;
import com.banka1.order.dto.ExchangeStatusDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * RestClient-based implementation of {@link StockClient}.
 * Active in all profiles except "local".
 */
@Component
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class StockClientImpl implements StockClient {

    private final RestClient stockRestClient;

    /**
     * {@inheritDoc}
     */
    @Override
    public StockListingDto getListing(Long id) {
        return stockRestClient.get()
                .uri("/api/listings/{id}", id)
                .retrieve()
                .body(StockListingDto.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StockExchangeDto getStockExchange(Long id) {
        return stockRestClient.get()
                .uri("/api/stock-exchanges/{id}", id)
                .retrieve()
                .body(StockExchangeDto.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean isStockExchangeOpen(Long id) {
        return stockRestClient.get()
                .uri("/api/stock-exchanges/{id}/is-open", id)
                .retrieve()
                .body(Boolean.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExchangeStatusDto getExchangeStatus(Long id) {
        // Assume the endpoint returns ExchangeStatusDto
        return stockRestClient.get()
                .uri("/api/stock-exchanges/{id}/status", id)
                .retrieve()
                .body(ExchangeStatusDto.class);
    }
}
