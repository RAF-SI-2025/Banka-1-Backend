package com.banka1.order.dto.client;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * DTO used to request an account transaction from account-service.
 */
@Getter
@AllArgsConstructor
public class PaymentDto {
    private String fromAccountNumber; // sender account
    private String toAccountNumber;   // receiver account
    private BigDecimal fromAmount;    // amount debited from sender (in sender currency)
    private BigDecimal toAmount;      // amount credited to receiver (in receiver currency)
    private BigDecimal commission;    // commission amount
    private Long clientId;            // id of the client initiating the transaction
}

