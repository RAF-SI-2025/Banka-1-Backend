package com.banka1.account_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ClientResponseDto {

    /** Identifikator klijenta. */
    private Long id;

    /** Ime klijenta. */
    private String ime;

    /** Prezime klijenta. */
    private String prezime;

    /** Datum rodjenja kao Unix timestamp. */
    private Long datumRodjenja;

    /** Pol klijenta. */
    private Pol pol;

    /** Email adresa klijenta. */
    private String email;

    /** Broj telefona klijenta. */
    private String brojTelefona;

    /** Adresa stanovanja klijenta. */
    private String adresa;
}