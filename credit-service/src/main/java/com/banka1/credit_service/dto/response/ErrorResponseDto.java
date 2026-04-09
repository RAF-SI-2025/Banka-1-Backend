package com.banka1.credit_service.dto.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardizovani DTO za odgovore sa greskama koji se vraca klijentu.
 * Sadrzi masinsko citljiv kod greske, kratak naslov, opis i opcione detalje validacije.
 * Polja sa {@code null} vrednoscu se preskaciju pri serijalizaciji.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDto {

    /**
     * Stabilan masinsko-citljivi identifikator greske (npr. {@code "ERR_USER_001"}).
     * Klijent moze koristiti ovaj kod za programsku obradu razlicitih scenarija greski
     * bez oslanjanja na tekst poruke koji se moze menjati (prevodi, prephrasing).
     */
    private String errorCode;

    /** Kratak, ljudski citljivi naziv greske. */
    private String errorTitle;

    /** Detaljan opis greske prosledjen iz poslovne logike. */
    private String errorDesc;

    /** Vreme nastanka greske. */
    private LocalDateTime timestamp = LocalDateTime.now();

    /** Mapa neispravnih polja i odgovarajucih poruka validacije (prisutna samo kod 400 validacionih gresaka). */
    private Map<String, String> validationErrors;

    /**
     * Kreira odgovor za opste ili biznis greske bez detalja validacije.
     *
     * @param errorCode stabilan kod greske za klijentsku obradu
     * @param errorTitle kratak naslov greske
     * @param errorDesc detaljan opis greske
     */
    public ErrorResponseDto(String errorCode, String errorTitle, String errorDesc) {
        this.errorCode = errorCode;
        this.errorTitle = errorTitle;
        this.errorDesc = errorDesc;
    }

    /**
     * Kreira odgovor za validacione greske sa mapom neispravnih polja.
     *
     * @param errorCode stabilan kod greske za klijentsku obradu
     * @param errorTitle kratak naslov greske
     * @param errorDesc detaljan opis greske
     * @param validationErrors mapa polja i poruka validacije
     */
    public ErrorResponseDto(String errorCode, String errorTitle, String errorDesc, Map<String, String> validationErrors) {
        this.errorCode = errorCode;
        this.errorTitle = errorTitle;
        this.errorDesc = errorDesc;
        this.validationErrors = validationErrors;
    }
}