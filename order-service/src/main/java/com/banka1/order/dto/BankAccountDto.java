package com.banka1.order.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * DTO representing a bank-owned internal account reference resolved through employee/account APIs.
 */
@Data
public class BankAccountDto {

    /** Internal identifier of the bank account. */
    @JsonAlias({"id", "accountId"})
    private Long accountId;
}
