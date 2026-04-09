package com.banka1.transaction_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO for creating a new payment transaction.
 * Contains all necessary details for initiating a payment.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class NewPaymentDto {

    /** Account number from which the payment is made. */
    @NotBlank(message = "Unesi racun posiljaoca")
    @Pattern(regexp = "^\\d{19}$", message = "Broj racuna mora imati 19 cifara")
    private String fromAccountNumber;

    /** Account number to which the payment is made. */
    @NotBlank(message = "Unesi racun primaoca")
    @Pattern(regexp = "^\\d{19}$", message = "Broj racuna mora imati 19 cifara")
    private String toAccountNumber;

    /** Amount to be transferred. */
    @NotNull(message = "Unesi iznos")
    private BigDecimal amount;

    /** Currency code of the source account. */
    @NotBlank(message = "Unesi naziv primaoca")
    private String recipientName;

    /** Payment code for categorization. */
    @NotNull(message = "Unesi sifru placanja")
    @Pattern(regexp = "^2.*", message = "Sifra mora poceti sa 2")
    @Pattern(regexp = "^\\d{3}$", message = "Sifra mora imati tacno 3 cifre")
    private String paymentCode;

    /** Reference number for the payment (optional - can be alphanumeric). */
    private String referenceNumber;

    /** Purpose of the payment. */
    @NotBlank(message = "Unesi svrhu placanja")
    private String paymentPurpose;

    /** ID of the verification session completed by the client. */
    @NotNull(message = "Unesi verification session ID")
    private Long verificationSessionId;

}
