package com.banka1.transaction_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing account details.
 * Contains information about a specific account.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AccountDetailsResponseDto {

    /** Account number associated with the account. */
    @JsonProperty("ownerId")
    private Long vlasnik;
}
