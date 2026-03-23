package com.banka1.account_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class UpdatedBalanceResponseDto {
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal dailySpending;
    private BigDecimal monthlySpending;
}
