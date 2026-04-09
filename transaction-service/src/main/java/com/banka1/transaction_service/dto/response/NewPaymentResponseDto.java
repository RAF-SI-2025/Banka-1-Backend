package com.banka1.transaction_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DTO representing the response for a new payment transaction.
 * Contains details about the created payment.
 */
@Getter
@AllArgsConstructor
public class NewPaymentResponseDto {

    /** Unique identifier of the created payment. */
    private String message;

    /** Status of the created payment. */
    private String status;
}

