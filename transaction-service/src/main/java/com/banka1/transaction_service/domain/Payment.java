package com.banka1.transaction_service.domain;

import com.banka1.transaction_service.domain.base.BaseEntityWithoutDelete;
import com.banka1.transaction_service.domain.enums.CurrencyCode;
import com.banka1.transaction_service.domain.enums.TransactionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// The idea with BaseEntityWithoutDelete is to encourage a good design pattern.
// If it turns out that Payment is the only one with this, I will remove it.

/**
 * JPA entity representing a financial transaction (payment) between two accounts.
 * Contains all relevant transaction data including identification details,
 * financial details, currency details, and transaction status.
 * Inherits from BaseEntityWithoutDelete, meaning it does not have a soft delete flag.
 */
@Entity
@Table(
        name = "payment_table"
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Payment extends BaseEntityWithoutDelete {

    /** Unique payment order number - automatically generated as "BANKA1-{id}" */
    @Column(unique = true)
    private String orderNumber;

    /** Account number from which the money is transferred (19 digits) */
    @NotBlank
    @Column(nullable = false)
    private String fromAccountNumber;

    /** Account number to which the money is transferred (19 digits) */
    @NotBlank
    @Column(nullable = false)
    private String toAccountNumber;

    /** Amount in the source currency */
    @Column(nullable = false)
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal initialAmount;

    /** Amount in the target currency after conversion */
    @Column(nullable = false)
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal finalAmount;

    /** Commission charged for the transaction */
    @Column(nullable = false)
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal commission;

    /** ID of the client who is the recipient of the money */
    @Column(nullable = false)
    private Long recipientClientId;

    /** Name of the recipient of the money */
    @NotBlank
    @Column(nullable = false)
    private String recipientName;

    /** Payment code - must start with 2 and have exactly 3 digits */
    @NotBlank
    @Pattern(regexp = "^2.*", message = "Sifra mora poceti sa 2")
    @Pattern(regexp = "^\\d{3}$", message = "Sifra mora imati tacno 3 cifre")
    @Column(nullable = false)
    private String paymentCode;

    /** Reference number for the payment (optional) */
    private String referenceNumber;

    /** Purpose/description of the payment */
    @NotBlank
    @Column(nullable = false)
    private String paymentPurpose;

    /** Current status of the transaction */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status=TransactionStatus.IN_PROGRESS;

    /** Currency of the source transaction */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurrencyCode fromCurrency;

    /** Currency of the target transaction */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurrencyCode toCurrency;

    /** Conversion rate between currencies (toAmount / fromAmount) */
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal exchangeRate;

}
