package com.banka1.credit_service.rest_client;



import com.banka1.credit_service.domain.enums.CurrencyCode;
import com.banka1.credit_service.dto.response.ConversionResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Service
public class ExchangeService {
    private final RestClient restClient;

    public ExchangeService(@Qualifier("exchangeClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ConversionResponseDto calculate(CurrencyCode fromCurrency,
                                           CurrencyCode toCurrency,
                                           BigDecimal amount) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/calculate")
                        .queryParam("fromCurrency", fromCurrency.name())
                        .queryParam("toCurrency", toCurrency.name())
                        .queryParam("amount", amount)
                        .build())
                .retrieve()
                .body(ConversionResponseDto.class);
    }



}
