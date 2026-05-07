package com.banka1.order.dto.client;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class CreditDebitBankDto {
    private String currencyCode;
    private BigDecimal amount;
}
