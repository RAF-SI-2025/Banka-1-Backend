package com.banka1.account_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO za zahtev validacije verifikacionog koda.
 * Sadrži ID sesije i kod koji je dao klijent.
 */
@Getter
@Setter
@AllArgsConstructor
public class ValidateRequest {
    /** ID sesije verifikacije za validaciju. */

    private Long sessionId;

    /** Verifikacioni kod koji je uneo klijent. */
    private String code;
}
