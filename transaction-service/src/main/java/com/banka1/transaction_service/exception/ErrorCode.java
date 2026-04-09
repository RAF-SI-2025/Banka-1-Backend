package com.banka1.transaction_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Enum centralizing all business errors of the application.
 * Each constant carries an HTTP status, a machine-readable code, and a short title
 * that are returned to the client via {@link BusinessException} and {@code GlobalExceptionHandler}.
 */
@Getter
public enum ErrorCode {

    // ── (ERR_ACCOUNT_xxx) ─────────────────────────────────────

    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT,"ERR_ACCOUNT_001","Nema dovoljno novca na racunu"),
    DAILY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT,"ERR_ACOCUNT_002","Predjen dnevni limit"),
    MONTHLY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT,"ERR_ACOCUNT_003","Predjen mesecni limit"),
    VERIFICATION_FAILED(HttpStatus.FORBIDDEN,"ERR_ACCOUNT_004","Neuspesna verifikacija");

    /** HTTP status returned to the client when this error is thrown. */
    private final HttpStatus httpStatus;

    /** Stable machine-readable identifier of the error (e.g., {@code "ERR_USER_001"}). */
    private final String code;

    /** Short human-readable title of the error. */
    private final String title;

    /**
     * Creates an error constant with the specified HTTP status, code, and title.
     *
     * @param httpStatus HTTP status returned to the client
     * @param code stable identifier of the error
     * @param title short title of the error
     */
    ErrorCode(HttpStatus httpStatus, String code, String title) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.title = title;
    }
}
