package com.banka1.credit_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO odgovora za endpoint kalkulacije ekvivalencije.
 * Struktura je prilagodjena specifikaciji zadatka i predstavlja javni API
 * kontrakt koji klijent dobija kao JSON odgovor.
 *
 * @param fromCurrency izvorna valuta
 * @param toCurrency ciljna valuta
 * @param fromAmount originalni iznos iz zahteva
 * @param toAmount preracunati iznos u ciljnoj valuti
 * @param rate efektivni kurs konverzije, tj. odnos {@code toAmount/fromAmount}
 * @param commission obracunata provizija u izvornoj valuti
 * @param date datum kursne liste koji je koriscen u obracunu
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
