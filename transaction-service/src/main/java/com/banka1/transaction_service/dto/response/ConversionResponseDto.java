package com.banka1.transaction_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO representing the result of a currency conversion.
 * Contains details about the conversion process and the resulting amounts.
 *
 * @param fromCurrency Source currency code.
 * @param toCurrency Target currency code.
 * @param fromAmount Amount in the source currency.
 * @param toAmount Amount in the target currency.
 * @param rate Conversion rate applied.
 * @param commission Calculated commission in the source currency.
 * @param date Date of the conversion.
 */
public record ConversionResponseDto(
        String fromCurrency,
        String toCurrency,
        BigDecimal fromAmount,
        BigDecimal toAmount,
        BigDecimal rate,
        BigDecimal commission,
        LocalDate date
) {
}
