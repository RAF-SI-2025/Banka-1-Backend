package com.banka1.userService.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public enum ErrorCode {
    // Definišemo sve moguće biznis greške na jednom mestu
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "ERR_USER_001", "Zaposleni nije pronađen"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "ERR_USER_002", "Email adresa je već u upotrebi"),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "ERR_USER_003", "Korisničko ime je već zauzeto"),
    NOT_STRONG_ROLE(HttpStatus.BAD_REQUEST,"ERR_USER_003","Nemas dovoljnu rolu");

    private final HttpStatus httpStatus;
    private final String code;
    private final String title;

    ErrorCode(HttpStatus httpStatus, String code, String title) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.title = title;
    }
}