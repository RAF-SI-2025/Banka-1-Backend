package com.banka1.verificationService.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Enum koji centralizuje sve poslovne greške aplikacije.
 * Svaka konstanta nosi HTTP status, mašinsko-čitljivi kod i kratak naslov.
 */
@Getter
public enum ErrorCode {

    // ── Greške vezane za verifikacione sesije (ERR_VERIFICATION_xxx) ──────────────────────────

    /** Verifikaciona sesija sa traženim identifikatorom nije pronađena u bazi. */
    VERIFICATION_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "ERR_VERIFICATION_001", "Sesija verifikacije nije pronađena"),

    /** Sesija verifikacije je otkazana. */
    VERIFICATION_SESSION_CANCELLED(HttpStatus.BAD_REQUEST, "ERR_VERIFICATION_002", "Sesija verifikacije je otkazana"),

    /** Sesija verifikacije je već verifikovana. */
    VERIFICATION_SESSION_ALREADY_VERIFIED(HttpStatus.BAD_REQUEST, "ERR_VERIFICATION_003", "Sesija verifikacije je već verifikovana"),

    /** Verifikacioni kod je istekao. */
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "ERR_VERIFICATION_004", "Verifikacioni kod je istekao"),

    /** Neispravan verifikacioni kod. */
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "ERR_VERIFICATION_005", "Neispravan verifikacioni kod");

    /** HTTP status koji se vraća klijentu kada se baci ova greška. */
    private final HttpStatus httpStatus;

    /** Stabilan mašinsko-čitljivi identifikator greške. */
    private final String code;

    /** Kratak ljudski čitljivi naslov greške. */
    private final String title;

    ErrorCode(HttpStatus httpStatus, String code, String title) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.title = title;
    }
}
