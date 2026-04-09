package com.banka1.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class UpdatedBalanceResponseDto {
    private BigDecimal senderBalance;
    private BigDecimal receiverBalance;
}

