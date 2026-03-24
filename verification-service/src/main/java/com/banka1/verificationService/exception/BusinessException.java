package com.banka1.verificationService.exception;

import lombok.Getter;

/**
 * Izuzetak koji predstavlja poslovnu grešku u aplikaciji.
 * Nosi {@link ErrorCode} koji sadrži HTTP status, mašinsko-čitljivi kod i naslov greške.
 */
@Getter
public class BusinessException extends RuntimeException {

    /** Kod greške sa HTTP statusom i opisom. */
    private final ErrorCode errorCode;

    /** Opciona dodatna poruka sa detaljima o grešci. */
    private final String details;

    /**
     * Kreira poslovnu grešku sa zadatim kodom i detaljima.
     *
     * @param errorCode kod greške
     * @param details   detalji greške
     */
    public BusinessException(ErrorCode errorCode, String details) {
        super(errorCode.getTitle() + ": " + details);
        this.errorCode = errorCode;
        this.details = details;
    }
}
