package com.banka1.transaction_service.dto.response;

import com.banka1.transaction_service.domain.enums.AccountOwnershipType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for searching account details.
 * Contains information about accounts matching the search criteria.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountSearchResponseDto {

    /** Account number of the matched account. */
    private String brojRacuna;

    /** Account holder's name. */
    private String ime;

    /** Account holder's surname. */
    private String prezime;

    /** Account ownership type (PERSONAL or BUSINESS). */
    private AccountOwnershipType accountOwnershipType;

    /** Account type (e.g., checking or foreign currency). */
    private String tekuciIliDevizni;

}
