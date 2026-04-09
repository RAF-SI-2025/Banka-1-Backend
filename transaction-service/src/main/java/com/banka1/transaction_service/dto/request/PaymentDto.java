package com.banka1.transaction_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO representing payment details.
 * Used for transferring payment information between layers.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PaymentDto {

    /** Account number from which the payment is made. */
    private String fromAccountNumber;

    /** Account number to which the payment is made. */
    private String toAccountNumber;

    /**
     * Amount involved in the payment.
     * If the accounts are in different currencies, this amount is converted according to toAmount.
     */
    private BigDecimal fromAmount;

    /**
     * Amount received on the destination account after conversion (if applied).
     * If the accounts are in the same currency, this value is equal to fromAmount.
     * If they are in different currencies, this value is converted according to the exchange rate.
     */
    private BigDecimal toAmount;

    /** Commission for the transaction. Usually deducted from the source account. */
    private BigDecimal commission;

    /** ID of the client initiating the transfer (optional, for audit log). */
    private Long clientId;

}
