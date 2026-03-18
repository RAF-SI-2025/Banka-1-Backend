package com.banka1.userService.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Enum koji predstavlja skup fine-grained permisija koje se dodeljuju zaposlenima.
 * Svaka konstanta opisuje jednu poslovnu operaciju koju zaposleni sme da izvrsava.
 */
@Getter
@AllArgsConstructor
public enum Permission {

    /** Osnovno poslovanje banke (pregledi, transakcije). */
    BANKING_BASIC("osnovno poslovanje banke"),

    /** Upravljanje klijentima (kreiranje, editovanje, brisanje). */
    CLIENT_MANAGE("upravljanje klientima"),

    /** Trgovina hartijama sa berze uz postavljene limite. */
    SECURITIES_TRADE_LIMITED("trgovina hartijama sa berze uz limite"),

    /** Trgovina hartijama sa berze bez ogranicenja. */
    SECURITIES_TRADE_UNLIMITED("trgovina hartijama sa berze bez limita"),

    /** Neogranicena trgovina. */
    TRADE_UNLIMITED("trgovina bez limita"),

    /** OTC (over-the-counter) trgovina – direktna razmena akcija i fjutursa. */
    OTC_TRADE("OTC (over-the-counter) - trgovina za direktnu trgovinu akcijama i futures-ima"),

    /** Upravljanje fondovima i agentima. */
    FUND_AGENT_MANAGE("upravljanje fondovima i agentima"),

    /** Administrativno upravljanje svim zaposlenima. */
    EMPLOYEE_MANAGE_ALL("upravlja svim zaposlenima");

    /** Citljiv opis permisije na srpskom jeziku. */
    private final String description;
}
