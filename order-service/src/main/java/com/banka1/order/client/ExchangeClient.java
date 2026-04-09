package com.banka1.order.client;

import com.banka1.order.dto.ExchangeRateDto;

import java.math.BigDecimal;

/**
 * Client interface for communicating with the exchange-service.
 * Used to convert amounts between currencies without applying a commission,
 * as required when checking agent daily limits for orders placed in foreign currencies.
 */
public interface ExchangeClient {

    /**
     * Calculates the converted amount between two currencies.
     *
     * @param fromCurrency source currency code (e.g. "USD")
     * @param toCurrency   target currency code (e.g. "RSD")
     * @param amount       the amount to convert
     * @return conversion result including the exchange rate and converted amount
     */
    ExchangeRateDto calculate(String fromCurrency, String toCurrency, BigDecimal amount);

    /**
     * Calculates the converted amount between two currencies without applying commission.
     *
     * @param fromCurrency source currency code
     * @param toCurrency   target currency code
     * @param amount       the amount to convert
     * @return conversion result for commission-free internal flows
     */
    ExchangeRateDto calculateWithoutCommission(String fromCurrency, String toCurrency, BigDecimal amount);
}
