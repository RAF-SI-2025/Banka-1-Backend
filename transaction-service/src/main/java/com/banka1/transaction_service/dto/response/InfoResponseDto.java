package com.banka1.transaction_service.dto.response;

import com.banka1.transaction_service.domain.enums.CurrencyCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO representing general information about a transaction.
 * Contains details such as transaction ID, status, and timestamps.
 */
@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
public class InfoResponseDto {

    /** Currency code of the source transaction */
    private CurrencyCode fromCurrencyCode;

    /** Currency code of the destination transaction */
    private CurrencyCode toCurrencyCode;

    /** ID of the owner of the source transaction */
    private Long fromVlasnik;

    /** ID of the owner of the destination transaction */
    private Long toVlasnik;

    /** Email address of the owner of the source transaction */
    private String fromEmail;

    /** Username of the owner of the source transaction */
    private String fromUsername;
}
