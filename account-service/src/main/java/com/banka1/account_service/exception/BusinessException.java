package com.banka1.account_service.exception;

import lombok.Getter;

/**
 * Izuzetak koji se baca za ocekivane greske poslovne logike.
 * Nosi strukturiran {@link ErrorCode} koji {@code GlobalExceptionHandler} mapira
 * na odgovarajuci HTTP status i telo odgovora.
 */
@Getter
public class BusinessException extends RuntimeException {

    /** Strukturiran kod greske koji opisuje tip i ozbiljnost poslovne greske. */
    private final ErrorCode errorCode;

    /**
     * Kreira biznis izuzetak sa pripadajucim kodom greske i detaljnom porukom.
     *
     * @param errorCode standardizovani kod domen-specificke greske
     * @param detailedMessage detaljna poruka za logovanje i klijentski odgovor
     */
    public BusinessException(ErrorCode errorCode, String detailedMessage) {
        super(detailedMessage);
        this.errorCode = errorCode;
    }
}
