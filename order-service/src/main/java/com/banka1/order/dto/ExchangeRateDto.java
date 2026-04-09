package com.banka1.order.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO representing the currency conversion result returned by the exchange-service.
 */
@Data
public class ExchangeRateDto {
    /** The amount after conversion to the target currency. */
    @JsonAlias("toAmount")
    private BigDecimal convertedAmount;
    /** The exchange rate applied. */
    @JsonAlias("rate")
    private BigDecimal exchangeRate;
    /** Source currency code. */
    private String fromCurrency;
    /** Target currency code. */
    private String toCurrency;
    /** Optional commission returned by exchange-service for public conversion endpoints. */
    private BigDecimal commission;
}
