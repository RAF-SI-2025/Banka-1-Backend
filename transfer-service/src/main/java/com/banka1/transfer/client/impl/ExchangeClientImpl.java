package com.banka1.transfer.client.impl;

import com.banka1.transfer.client.ExchangeClient;
import com.banka1.transfer.dto.client.ExchangeResponseDto;
import com.banka1.transfer.exception.BusinessException;
import com.banka1.transfer.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;


/**
 * Implementacija klijenta za menjačnicu koja koristi query parametre za kalkulaciju kursa.
 */
@Component
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class ExchangeClientImpl implements ExchangeClient {

    private final RestClient exchangeRestClient;

    @Override
    public ExchangeResponseDto calculateExchange(String fromCurrency, String toCurrency, BigDecimal amount) {
        try {
            return exchangeRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/exchange/calculate")
                            .queryParam("fromCurrency", fromCurrency)
                            .queryParam("toCurrency", toCurrency)
                            .queryParam("amount", amount)
                            .build())
                    .retrieve()
                    .body(ExchangeResponseDto.class);
        } catch (HttpClientErrorException e) {
            // Ako menjačnica vrati 4xx, verovatno je neispravna valuta
            log.error("Exchange service error: {}", e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Greška u menjačnici: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Exchange service unavailable: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND, "Servis menjačnice trenutno nije dostupan.");
        }
    }
}