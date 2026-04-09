package com.banka1.credit_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO koji mapira query parametre endpoint-a
 * {@code GET /calculate?fromCurrency=...&toCurrency=...&amount=...}.
 * Spring MVC automatski binduje vrednosti iz URL query string-a u ovo polje
 * i zatim pokrece Bean Validation anotacije definisane nad poljima.
 */
@Getter
@Setter
public class ConversionQueryDto {

    private static final String SUPPORTED_CURRENCY_REGEX = "^(?i)(RSD|EUR|CHF|USD|GBP|JPY|CAD|AUD)$";
    private static final String SUPPORTED_CURRENCY_MESSAGE =
            "Supported currencies are RSD, EUR, CHF, USD, GBP, JPY, CAD and AUD.";

    /**
     * Izvorna valuta iz koje korisnik konvertuje iznos.
     */

    private String fromCurrency;

    /**
     * Ciljna valuta u koju se obracunava ekvivalent.
     */

    private String toCurrency;

    /**
     * Iznos koji treba preracunati.
     */

    private BigDecimal amount;

    /**
     * Opcioni datum kursne liste.
     * Ako nije zadat, koristi se poslednji raspolozivi lokalni snapshot.
     */

    private LocalDate date;
}
