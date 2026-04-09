package com.banka1.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for performing a transaction between accounts.
 */
@Data
public class AccountTransactionRequest {
    /** ID of the account to debit. */
    private Long fromAccountId;

    /** ID of the account to credit. */
    private Long toAccountId;

    /** Amount to transfer. */
    private BigDecimal amount;

    /** Currency of the transaction. */
    private String currency;

    /** Description of the transaction. */
    private String description;
}
