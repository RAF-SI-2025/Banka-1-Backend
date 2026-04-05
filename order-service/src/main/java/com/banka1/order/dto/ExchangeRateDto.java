package com.banka1.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO representing the currency conversion result returned by the exchange-service.
 */
@Data
public class ExchangeRateDto {
    /** The amount after conversion to the target currency. */
    private BigDecimal convertedAmount;
    /** The exchange rate applied. */
    private BigDecimal exchangeRate;
    /** Source currency code. */
    private String fromCurrency;
    /** Target currency code. */
    private String toCurrency;
}
