package com.banka1.exchangeService.controller;

import com.banka1.exchangeService.advice.GlobalExceptionHandler;
import com.banka1.exchangeService.dto.ConversionRequestDto;
import com.banka1.exchangeService.dto.ConversionResponseDto;
import com.banka1.exchangeService.dto.ExchangeRateDto;
import com.banka1.exchangeService.dto.ExchangeRateFetchResponseDto;
import com.banka1.exchangeService.exception.BusinessException;
import com.banka1.exchangeService.exception.ErrorCode;
import com.banka1.exchangeService.service.ExchangeRateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvc testovi za javni REST kontrakt ExchangeRateController-a.
 * Ovi testovi proveravaju rute, status kodove, validaciju query/body podataka
 * i osnovni JSON oblik odgovora koji vide klijenti i gateway.
 */
@WebMvcTest(ExchangeRateController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class ExchangeRateControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExchangeRateService exchangeRateService;

    /**
     * Proverava da rucni fetch endpoint vraca rezultat servisnog sloja u
     * ispravnom JSON formatu.
     * Prolaz znaci da operater ili drugi servis mogu rucno da pokrenu refresh i
     * dobiju listu sacuvanih kurseva.
     */
    @Test
    void fetchRatesReturnsStoredRates() throws Exception {
        when(exchangeRateService.fetchAndStoreDailyRates())
                .thenReturn(new ExchangeRateFetchResponseDto(7, List.of(
                        rate("EUR", "117.10", "117.90"),
                        rate("USD", "108.10", "108.90")
                ), false, LocalDate.of(2026, 3, 22)));

        mockMvc.perform(post("/rates/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetchedCount").value(7))
                .andExpect(jsonPath("$.fallbackUsed").value(false))
                .andExpect(jsonPath("$.rates[0].currencyCode").exists());
    }

    /**
     * Proverava citanje kursne liste za prosledjeni datum.
     * Prolaz znaci da endpoint `GET /rates` pravilno mapira query parametar i
     * serializuje listu DTO objekata.
     */
    @Test
    void getRatesReturnsSnapshotList() throws Exception {
        when(exchangeRateService.getRates(LocalDate.of(2026, 3, 22)))
                .thenReturn(List.of(rate("EUR", "117.10", "117.90")));

        mockMvc.perform(get("/rates").param("date", "2026-03-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currencyCode").value("EUR"));
    }

    /**
     * Proverava da biznis greska iz servisnog sloja bude prevedena u 404 i
     * standardni error payload.
     * Prolaz znaci da klijent dobija stabilan odgovor kada trazeni kurs ne
     * postoji.
     */
    @Test
    void getRateReturnsNotFoundWhenBusinessExceptionIsThrown() throws Exception {
        when(exchangeRateService.getRate("EUR", null))
                .thenThrow(new BusinessException(ErrorCode.EXCHANGE_RATE_NOT_FOUND, "missing"));

        mockMvc.perform(get("/rates/EUR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_EXCHANGE_RATE_NOT_FOUND"));
    }

    /**
     * Proverava uspešan scenario za endpoint kalkulacije sa query parametrima.
     * Prolaz znaci da se `fromCurrency`, `toCurrency`, `amount` i opcioni `date`
     * pravilno binduju i vracaju kao JSON odgovor iz kontrolera.
     */
    @Test
    void calculateReturnsCalculatedResponse() throws Exception {
        when(exchangeRateService.convert(any(ConversionRequestDto.class)))
                .thenReturn(new ConversionResponseDto(
                        "EUR",
                        "USD",
                        new BigDecimal("100.00"),
                        new BigDecimal("108.33333333"),
                        new BigDecimal("1.08333333"),
                        new BigDecimal("0.70"),
                        LocalDate.of(2026, 3, 22)
                ));

        mockMvc.perform(get("/calculate")
                        .param("fromCurrency", "EUR")
                        .param("toCurrency", "USD")
                        .param("amount", "100.00")
                        .param("date", "2026-03-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCurrency").value("EUR"))
                .andExpect(jsonPath("$.toCurrency").value("USD"))
                .andExpect(jsonPath("$.toAmount").value(108.33333333))
                .andExpect(jsonPath("$.commission").value(0.70));
    }

    /**
     * Proverava da interni commission-free endpoint postoji i mapira se na
     * namenski servisni metod za porez/settlement tokove.
     */
    @Test
    void calculateWithoutCommissionReturnsZeroCommissionResponse() throws Exception {
        when(exchangeRateService.convertWithoutCommission(any(ConversionRequestDto.class)))
                .thenReturn(new ConversionResponseDto(
                        "USD",
                        "RSD",
                        new BigDecimal("37.50"),
                        new BigDecimal("2925.00000000"),
                        new BigDecimal("78.00000000"),
                        BigDecimal.ZERO,
                        LocalDate.of(2026, 3, 22)
                ));

        mockMvc.perform(get("/internal/calculate/no-commission")
                        .param("fromCurrency", "USD")
                        .param("toCurrency", "RSD")
                        .param("amount", "37.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toAmount").value(2925.00000000))
                .andExpect(jsonPath("$.commission").value(0));
    }

    /**
     * Proverava da neispravni query parametri za kalkulaciju vracaju
     * `ERR_VALIDATION` i mapu konkretnih polja.
     * Prolaz znaci da je javni API kontrakt validacionih gresaka stabilan.
     */
    @Test
    void calculateReturnsBadRequestForInvalidQuery() throws Exception {
        mockMvc.perform(get("/calculate")
                        .param("fromCurrency", "NOK")
                        .param("toCurrency", "SEK")
                        .param("amount", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_VALIDATION"))
                .andExpect(jsonPath("$.validationErrors.amount").exists())
                .andExpect(jsonPath("$.validationErrors.fromCurrency").exists())
                .andExpect(jsonPath("$.validationErrors.toCurrency").exists());
    }

    /**
     * Pomocna metoda za pravljenje stabilnog test DTO objekta kursa.
     */
    private ExchangeRateDto rate(String currencyCode, String buyingRate, String sellingRate) {
        return new ExchangeRateDto(
                currencyCode,
                new BigDecimal(buyingRate),
                new BigDecimal(sellingRate),
                LocalDate.of(2026, 3, 22),
                Instant.parse("2026-03-22T10:15:30Z")
        );
    }
}
