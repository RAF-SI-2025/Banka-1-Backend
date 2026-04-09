package com.banka1.transaction_service.rest_client;

import com.banka1.transaction_service.domain.enums.CurrencyCode;
import com.banka1.transaction_service.dto.response.ConversionResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * REST client for interacting with the Exchange Service.
 * Provides methods for retrieving exchange rates.
 */
@Service
public class ExchangeService {

    private final RestClient restClient;

    /**
     * Constructor that injects the REST client for the Exchange Service.
     *
     * @param restClient configured REST client with JWT authentication
     */
    public ExchangeService(@Qualifier("exchangeClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Calculates the equivalent amount when converting between two currencies.
     * <p>
     * The calculator uses current exchange rates and applies a commission.
     *
     * @param fromCurrency the source currency
     * @param toCurrency the target currency
     * @param amount the amount to convert in the source currency
     * @return the conversion result with all details (converted amount, rate, commission)
     */
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
