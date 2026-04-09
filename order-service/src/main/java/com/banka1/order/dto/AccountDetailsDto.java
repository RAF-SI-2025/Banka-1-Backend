package com.banka1.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO representing account details returned by the account-service.
 */
@Data
public class AccountDetailsDto {
    /** The unique account number. */
    private String accountNumber;
    /** Current available balance. */
    private BigDecimal balance;
    /** Currency code of the account (e.g. "RSD", "USD"). */
    private String currency;
    /** Internal ID of the account owner. */
    private Long ownerId;
    /** Approved credit available for margin-backed trading. */
    private BigDecimal availableCredit;
}
