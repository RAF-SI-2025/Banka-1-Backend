package com.banka1.transaction_service.dto.response;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO za odgovor sa detaljnim informacijama o bankarskom računu.
 * <p>
 * Sadrži sve relevantne informacije o računu uključujući:
 * <ul>
 *   <li>Identifikacione podatke (broj, naziv, vlasnik)</li>
 *   <li>Finansijske podatke (stanje, raspoloživo stanje, limitri, trošenja)</li>
 *   <li>Statusne podatke (status, valuta, datum kreiranja)</li>
 *   <li>Podatke o firmi (ako je to poslovni račun)</li>
 *   <li>Kartice vezane za račun</li>
 * </ul>
 * <p>
 * Koristi se u svim odgovorima gde klijent traži detaljne informacije o računu.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AccountDetailsResponseDto {


    /** ID vlasnika računa (klijenta). */
    @JsonProperty("ownerId")
    private Long vlasnik;



}
