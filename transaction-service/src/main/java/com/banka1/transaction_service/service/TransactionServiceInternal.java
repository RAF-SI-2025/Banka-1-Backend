package com.banka1.transaction_service.service;

import com.banka1.transaction_service.domain.enums.TransactionStatus;
import com.banka1.transaction_service.dto.request.NewPaymentDto;
import com.banka1.transaction_service.dto.response.ConversionResponseDto;
import com.banka1.transaction_service.dto.response.InfoResponseDto;
import com.banka1.transaction_service.dto.response.UpdatedBalanceResponseDto;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Internal interface that defines operations for managing the transaction lifecycle.
 * Used internally for creating and finishing transactions with notifications.
 */
public interface TransactionServiceInternal {

    /**
     * Creates a new transaction in the database with all relevant details.
     * <p>
     * This method:
     * <ul>
     *   <li>Creates a Payment entity with all data from the DTO</li>
     *   <li>Sets the status to IN_PROGRESS</li>
     *   <li>Generates a unique serial number as "BANKA1-{id}"</li>
     *   <li>Saves to the database and returns the ID</li>
     * </ul>
     *
     * @param jwt JWT token of the user initiating the transaction
     * @param newPaymentDto DTO with payment details
     * @param infoResponseDto information about accounts (owners, currencies, email)
     * @param conversionResponseDto result of currency conversion (amount, rate, commission)
     * @return ID of the newly created Payment entity
     */
    Long create(Jwt jwt, NewPaymentDto newPaymentDto, InfoResponseDto infoResponseDto, ConversionResponseDto conversionResponseDto);

    /**
     * Finishes a transaction with a final status and sends an email notification.
     * <p>
     * This method:
     * <ul>
     *   <li>Updates the transaction status to COMPLETED or DENIED</li>
     *   <li>Registers a TransactionSynchronization callback</li>
     *   <li>After commit, sends a RabbitMQ message for email notification</li>
     * </ul>
     *
     * @param jwt JWT token of the user
     * @param infoResponseDto information about the sender (email, username)
     * @param id ID of the Payment entity to finish
     * @param transactionStatus final status of the transaction (COMPLETED or DENIED)
     */
    void finish(Jwt jwt, InfoResponseDto infoResponseDto, Long id, TransactionStatus transactionStatus);
}
