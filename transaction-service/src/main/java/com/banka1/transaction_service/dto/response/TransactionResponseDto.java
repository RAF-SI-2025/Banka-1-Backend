package com.banka1.transaction_service.dto.response;

import com.banka1.transaction_service.domain.Payment;
import com.banka1.transaction_service.domain.enums.CurrencyCode;
import com.banka1.transaction_service.domain.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO representing the response for a transaction.
 * Contains details about the transaction such as amounts, accounts, and status.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TransactionResponseDto {

    /** Unique identifier of the transaction. */
    private String orderNumber;

    /** Account number from which the transaction was initiated. */
    private String fromAccountNumber;

    /** Account number to which the transaction was directed. */
    private String toAccountNumber;

    /** Amount involved in the transaction. */
    private BigDecimal initialAmount;

    /** Amount involved in the transaction. */
    private BigDecimal finalAmount;

    /** Recipient's name. */
    private String recipientName;

    /** Payment code. */
    private String paymentCode;

    /** Reference number for the payment. */
    private String referenceNumber;

    /** Purpose or description of the payment. */
    private String paymentPurpose;

    /** Status of the transaction. */
    private TransactionStatus status;

    /** Currency code of the transaction. */
    private CurrencyCode fromCurrency;

    /** Currency code of the transaction. */
    private CurrencyCode toCurrency;

    /** Exchange rate between currencies. */
    private BigDecimal exchangeRate;

    /** Timestamp when the transaction was created. */
    private LocalDateTime createdAt;

    /**
     * Constructor for converting a Payment entity to this DTO.
     *
     * @param payment Payment entity from the database
     */
    public TransactionResponseDto(Payment payment) {
        this.orderNumber = payment.getOrderNumber();
        this.fromAccountNumber = payment.getFromAccountNumber();
        this.toAccountNumber = payment.getToAccountNumber();
        this.initialAmount = payment.getInitialAmount();
        this.finalAmount = payment.getFinalAmount();
        this.recipientName = payment.getRecipientName();
        this.paymentCode = payment.getPaymentCode();
        this.referenceNumber = payment.getReferenceNumber();
        this.paymentPurpose = payment.getPaymentPurpose();
        this.status = payment.getStatus();
        this.fromCurrency = payment.getFromCurrency();
        this.toCurrency = payment.getToCurrency();
        this.exchangeRate = payment.getExchangeRate();
        this.createdAt = payment.getCreatedAt();
    }
}
