package com.banka1.account_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Enum koji centralizuje sve poslovne greske aplikacije.
 * Svaka konstanta nosi HTTP status, masinsko-citljivi kod i kratak naslov koji se
 * vracaju klijentu putem {@link BusinessException} i {@code GlobalExceptionHandler}-a.
 */
@Getter
public enum ErrorCode {

    // ── (ERR_ACCOUNT_xxx) ─────────────────────────────────────

    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT,"ERR_ACCOUNT_001","Nema dovoljno novca na racunu"),
    DAILY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT,"ERR_ACOCUNT_002","Predjen dnevni limit"),
    MONTHLY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT,"ERR_ACOCUNT_003","Predjen mesecni limit"),
    VERIFICATION_FAILED(HttpStatus.FORBIDDEN,"ERR_ACCOUNT_004","Neuspesna verifikacija");

    /** HTTP status koji se vraca klijentu kada se baci ova greska. */
    private final HttpStatus httpStatus;

    /** Stabilan masinsko-citljivi identifikator greske (npr. {@code "ERR_USER_001"}). */
    private final String code;

    /** Kratak ljudski citljivi naslov greske. */
    private final String title;

    /**
     * Kreira konstantu greske sa zadatim HTTP statusom, kodom i naslovom.
     *
     * @param httpStatus HTTP status koji se vraca klijentu
     * @param code stabilan identifikator greske
     * @param title kratak naslov greske
     */
    ErrorCode(HttpStatus httpStatus, String code, String title) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.title = title;
    }
}
