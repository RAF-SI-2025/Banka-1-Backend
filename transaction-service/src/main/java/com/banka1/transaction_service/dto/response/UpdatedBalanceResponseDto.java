package com.banka1.transaction_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO representing the response for an updated account balance.
 * Contains details about the account and the new balance.
 */
@Getter
@Setter
@AllArgsConstructor
public class UpdatedBalanceResponseDto {

    /** New balance of the sender's account after the transaction */
    private BigDecimal senderBalance;

    /** New balance of the receiver's account after the transaction */
    private BigDecimal receiverBalance;
}
