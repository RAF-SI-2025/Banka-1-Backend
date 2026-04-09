package com.banka1.exchangeService.controller;

import com.banka1.exchangeService.dto.ConversionQueryDto;
import com.banka1.exchangeService.dto.ConversionRequestDto;
import com.banka1.exchangeService.dto.ConversionResponseDto;
import com.banka1.exchangeService.dto.ExchangeRateDto;
import com.banka1.exchangeService.dto.ExchangeRateFetchResponseDto;
import com.banka1.exchangeService.service.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for managing locally stored daily exchange rates and currency conversions.
 * Public routes within the service have no gateway prefix: {@code /rates},
 * {@code /rates/{currencyCode}}, and {@code /calculate}.
 * When requests pass through the API Gateway, endpoints are accessible under
 * the prefix {@code /api/exchange}.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Exchange Rates", description = "Exchange rate storage and conversion endpoints")
public class ExchangeRateController {

    /**
     * Service for exchange rate management and currency conversions.
     */
    private final ExchangeRateService exchangeRateService;

    /**
     * Manually triggers fetching of daily exchange rates for all supported foreign currencies
     * and stores them as a local snapshot. This endpoint uses the same logic as the
     * scheduled daily cron job. Requires ADMIN role.
     *
     * @return result of the fetch operation including the number of processed currencies
     *         and the list of stored rates
     */
    @PostMapping("/rates/fetch")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE')")
    @Operation(
            summary = "Fetch and store daily exchange rates",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "Rates fetched and stored",
                    content = @Content(schema = @Schema(implementation = ExchangeRateFetchResponseDto.class))
            )
    )
    public ExchangeRateFetchResponseDto fetchRates() {
        return exchangeRateService.fetchAndStoreDailyRates();
    }

    /**
     * Retrieves the list of exchange rates for the requested date, or the most recently
     * available local snapshot if no date is provided.
     *
     * @param date optional snapshot date; if null, returns the latest available snapshot
     * @return list of daily exchange rates sorted by currency code
     */
    @GetMapping("/rates")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE')")
    @Operation(
            summary = "Get stored exchange rates",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "Stored exchange rates",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ExchangeRateDto.class)))
            )
    )
    public List<ExchangeRateDto> getRates(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return exchangeRateService.getRates(date);
    }

    /**
     * Retrieves the exchange rate for a single specific currency for the requested date,
     * or the most recently available local snapshot if no date is provided.
     *
     * @param currencyCode three-letter ISO currency code, for example {@code EUR}
     * @param date optional snapshot date; if null, returns the latest available snapshot
     * @return exchange rate record for the requested currency
     */
    @GetMapping("/rates/{currencyCode}")
    @Operation(summary = "Get a single exchange rate")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE')")

    public ExchangeRateDto getRate(
            @PathVariable String currencyCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return exchangeRateService.getRate(currencyCode, date);
    }

    /**
     * Calculates the equivalent of an amount from one currency to another.
     * Conversion always goes through RSD as the base currency, using the selling rate
     * for non-RSD currencies according to business rules.
     * Input parameters are taken from URL query parameters, for example:
     * {@code /calculate?fromCurrency=EUR&toCurrency=USD&amount=100}.
     *
     * @param request query DTO that Spring automatically populates from URL query parameters
     * @return conversion result including output amount, effective rate, commission, and rate date
     */
    @GetMapping("/calculate")
    @Operation(summary = "Calculate currency equivalence via RSD base")
    @PreAuthorize("hasAnyRole('ADMIN','SERVICE')")
    public ConversionResponseDto calculate(@Valid ConversionQueryDto request) {
        return exchangeRateService.convert(new ConversionRequestDto(
                request.getAmount(),
                request.getFromCurrency(),
                request.getToCurrency(),
                request.getDate()
        ));
    }

    /**
     * Calculates a currency conversion without commission for internal flows such as tax charging.
     *
     * @param request query DTO that Spring automatically populates from URL query parameters
     * @return commission-free conversion result
     */
    @GetMapping("/internal/calculate/no-commission")
    @Operation(summary = "Calculate currency equivalence via RSD base without commission")
    @PreAuthorize("hasRole('SERVICE')")
    public ConversionResponseDto calculateWithoutCommission(@Valid ConversionQueryDto request) {
        return exchangeRateService.convertWithoutCommission(new ConversionRequestDto(
                request.getAmount(),
                request.getFromCurrency(),
                request.getToCurrency(),
                request.getDate()
        ));
    }
}
