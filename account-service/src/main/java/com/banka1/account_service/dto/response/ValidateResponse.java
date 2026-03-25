package com.banka1.account_service.dto.response;

import com.banka1.account_service.domain.enums.VerificationStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO za odgovor nakon validacije verifikacionog koda.
 * Ukazuje da li je kod bio validan, trenutni status sesije i preostale pokušaje.
 */
@Getter
@Setter
public class ValidateResponse {
    /** Da li dati kod odgovara sačuvanom kodu. */
    private boolean valid;

    /** Trenutni status sesije verifikacije nakon validacije. */
    private VerificationStatus status;

    /** Broj preostalih pokušaja pre otkazivanja (0 ako je verifikovano ili otkazano). */
    private int remainingAttempts;

    /**
     * Konstruktor za kreiranje odgovora validacije.
     *
     * @param valid true ako je kod bio tačan
     * @param status trenutni status sesije
     * @param remainingAttempts pokušaji preostali pre otkazivanja
     */
    public ValidateResponse(boolean valid, VerificationStatus status, int remainingAttempts) {
        this.valid = valid;
        this.status = status;
        this.remainingAttempts = remainingAttempts;
    }
}
