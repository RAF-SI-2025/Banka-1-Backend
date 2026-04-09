package com.banka1.exchangeService.service;

import com.banka1.exchangeService.dto.ConversionRequestDto;
import com.banka1.exchangeService.dto.ConversionResponseDto;
import com.banka1.exchangeService.dto.ExchangeRateDto;
import com.banka1.exchangeService.dto.ExchangeRateFetchResponseDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Service interface for managing exchange rates and currency conversions.
 * Provides operations for fetching daily rates from external providers,
 * retrieving locally stored rates, and converting between currencies.
 */
public interface ExchangeRateService {

    /**
     * Fetches daily exchange rates from an external API and stores them locally.
     * Typically invoked by a scheduler daily at 08:00 UTC.
     * If the external fetch fails, uses the latest local snapshot as a fallback for the new daily entry.
     *
     * @return result of the fetch operation including stored exchange rates
     */
    ExchangeRateFetchResponseDto fetchAndStoreDailyRates();

    /**
     * Retrieves all exchange rates for the requested date or the latest available snapshot.
     *
     * @param date snapshot date; if {@code null}, returns the latest available date
     * @return list of exchange rates
     */
    List<ExchangeRateDto> getRates(LocalDate date);

    /**
     * Retrieves the exchange rate for a single currency for the given date
     * or the latest available snapshot if no date is provided.
     *
     * @param currencyCode three-letter ISO currency code
     * @param date snapshot date; if {@code null}, returns the latest available date
     * @return exchange rate for the specified currency
     */
    ExchangeRateDto getRate(String currencyCode, LocalDate date);

    /**
     * Converts an amount from one currency to another using locally stored rates.
     * Conversion always uses RSD as the base currency according to business rules.
     *
     * @param request conversion request containing source/target currencies and amount
     * @return conversion result with output amount, effective rate, and commission
     */
    ConversionResponseDto convert(ConversionRequestDto request);

    /**
     * Converts an amount from one currency to another without applying commission.
     * Intended for internal settlement and tax flows that must stay commission-free.
     *
     * @param request conversion request containing source/target currencies and amount
     * @return conversion result with zero commission
     */
    ConversionResponseDto convertWithoutCommission(ConversionRequestDto request);
}
