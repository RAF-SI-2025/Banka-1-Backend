package com.banka1.order.dto.client;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class CreditDebitAccountDto {
    private String accountNumber;
    private BigDecimal amount;
    private Long clientId;
}
