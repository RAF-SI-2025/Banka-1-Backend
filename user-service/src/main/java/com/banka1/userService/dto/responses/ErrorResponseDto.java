package com.banka1.userService.dto.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDto {
    /**
     *  ErrorCode - robusniji nacin da se identifikuje greska na frontendu, npr. "VALIDATION_FAILED", "SESSION_INVALID"
     *  Umesto da se oslanjamo samo na errorTitle koji je vise "human readable" i moze da se menja (recimo prevodi),
     *  errorCode ce biti konstantan i lako je uraditi switch(errorCode) -> handleCode ako front treba da eksplicitno hendluje neku vrstu greske kod sebe
     *  Ne bi bilo lose da inkorporiramo ovo u kordinaciji sa frontom, ali ako ne zelimo, mozemo samo slati null
     *  Verovatno je bolje da ih i mi i front cuvamo kao enum, ali da se parsuju u string radi prenosa preko JSON-a.
     *
     */
    private String errorCode;

    private String errorTitle;

    private String errorDesc;

    private LocalDateTime timestamp = LocalDateTime.now();

    private Map<String,String> validationErrors;

    // Konstruktor za biznis logiku
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

    // Konstruktor za validacione greške
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
