package com.banka1.exchangeService.client;

import com.banka1.exchangeService.config.ExchangeRateProperties;
import com.banka1.exchangeService.dto.TwelveDataRateResponse;
import com.banka1.exchangeService.exception.BusinessException;
import com.banka1.exchangeService.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Klijent za dohvat deviznog kursa sa Twelve Data servisa.
 */
@Component
@RequiredArgsConstructor
public class TwelveDataClient {

    /**
     * Namenski HTTP klijent konfigurisan za Twelve Data bazni URL.
     */
    private final RestClient twelveDataRestClient;

    /**
     * Konfiguraciona svojstva za pristup Twelve Data API-ju.
     */
    private final ExchangeRateProperties exchangeRateProperties;

    /**
     * Dohvata kurs za trazeni valutni par.
     *
     * @param fromCurrency izvorna valuta
     * @param toCurrency   ciljna valuta
     * @return parsiran odgovor eksternog servisa
     */
    public TwelveDataRateResponse fetchExchangeRate(String fromCurrency, String toCurrency) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = twelveDataRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/exchange_rate")
                            .queryParam("symbol", fromCurrency + "/" + toCurrency)
                            .queryParam("apikey", exchangeRateProperties.twelveDataApiKey())
                            .build())
                    .retrieve()
                    .body(Map.class);

            return parseResponse(body, fromCurrency, toCurrency);
        } catch (RestClientException ex) {
            throw new BusinessException(
                    ErrorCode.EXCHANGE_RATE_FETCH_FAILED,
                    "Failed to connect to Twelve Data service."
            );
        }
    }

    /**
     * Validira i mapira raw JSON odgovor Twelve Data endpoint-a u interni DTO model `TwelveDataRateResponse`.
     *
     * @param body         deserializovan odgovor eksternog servisa
     * @param fromCurrency ocekivana izvorna valuta
     * @param toCurrency   ocekivana ciljna valuta
     * @return parsiran odgovor sa kursom i datumom snapshot-a
     */
    private TwelveDataRateResponse parseResponse(Map<String, Object> body, String fromCurrency, String toCurrency) {
        if (body == null) {
            throw new BusinessException(
                    ErrorCode.EXCHANGE_RATE_FETCH_FAILED,
                    "Twelve Data returned no response."
            );
        }

        if (body.containsKey("code") || body.containsKey("message") || body.containsKey("status")) {
            String message = String.valueOf(body.getOrDefault("message", "Unknown Twelve Data error."));
            throw new BusinessException(ErrorCode.EXCHANGE_RATE_FETCH_FAILED, message);
        }

        String symbol = readRequiredString(body, "symbol");
        BigDecimal rate = readDecimal(body, "rate");

        String expectedSymbol = fromCurrency + "/" + toCurrency;
        if (!expectedSymbol.equalsIgnoreCase(symbol)) {
            throw new BusinessException(
                    ErrorCode.EXCHANGE_RATE_FETCH_FAILED,
                    "Twelve Data returned unexpected currency pair " + symbol + "."
            );
        }

        LocalDate rateDate = LocalDate.now(ZoneOffset.UTC);

        Object timestampRaw = body.get("timestamp");
        if (timestampRaw != null && !String.valueOf(timestampRaw).isBlank()) {
            try {
                long epochSeconds = Long.parseLong(String.valueOf(timestampRaw));
                rateDate = Instant.ofEpochSecond(epochSeconds)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate();
            } catch (NumberFormatException ex) {
                throw new BusinessException(
                        ErrorCode.EXCHANGE_RATE_FETCH_FAILED,
                        "Twelve Data returned invalid timestamp."
                );
            }
        }

        return new TwelveDataRateResponse(
                fromCurrency,
                toCurrency,
                rate,
                rateDate
        );
    }

    /**
     * Cita decimalno polje iz Twelve Data odgovora.
     * Prosledimo mu fieldName = "rate", za "rate": "117.42, vraticemo BigDecimal("117.42")
     * Poenta je da to uradimo ovde jednom, a ne da dupliramo proveru u main kodu sa IF-ovima
     *
     * @param node      mapa polja odgovora
     * @param fieldName naziv obaveznog polja (npr. rate)
     * @return parsirana decimalna vrednost
     */
    private BigDecimal readDecimal(Map<String, Object> node, String fieldName) {
        Object field = node.get(fieldName);
        if (field == null || String.valueOf(field).isBlank()) {
            throw new BusinessException(
                    ErrorCode.EXCHANGE_RATE_FETCH_FAILED,
                    "Twelve Data response missing field " + fieldName + "."
            );
        }
        try {
            return new BigDecimal(String.valueOf(field));
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    ErrorCode.EXCHANGE_RATE_FETCH_FAILED,
                    "Twelve Data response contains invalid decimal field " + fieldName + "."
            );
        }
    }

    /**
     * Cita obavezno string polje iz Twelve Data odgovora.
     * Isti princip kao rada kao readDecimal.
     *
     * @param node      mapa polja odgovora
     * @param fieldName naziv obaveznog polja
     * @return neprazna string vrednost
     */
    private String readRequiredString(Map<String, Object> node, String fieldName) {
        Object field = node.get(fieldName);
        if (field == null || String.valueOf(field).isBlank()) {
            throw new BusinessException(
                    ErrorCode.EXCHANGE_RATE_FETCH_FAILED,
                    "Twelve Data response missing field " + fieldName + "."
            );
        }
        return String.valueOf(field);
    }
}
