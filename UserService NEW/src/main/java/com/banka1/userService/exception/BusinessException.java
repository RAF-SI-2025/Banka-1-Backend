package com.banka1.userService.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
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
