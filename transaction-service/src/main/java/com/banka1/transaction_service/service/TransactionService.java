package com.banka1.transaction_service.service;

import com.banka1.transaction_service.domain.enums.TransactionStatus;
import com.banka1.transaction_service.dto.request.ApproveDto;
import com.banka1.transaction_service.dto.request.NewPaymentDto;
import com.banka1.transaction_service.dto.response.NewPaymentResponseDto;
import com.banka1.transaction_service.dto.response.TransactionResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Service interface for managing transactions.
 * Provides methods for creating, finding, and searching transactions.
 */
public interface TransactionService {

    /**
     * Creates a new transaction (payment) between two accounts.
     *
     * @param jwt JWT token of the authenticated user
     * @param newPaymentDto DTO with new payment details
     * @return response with status and payment message
     */
    NewPaymentResponseDto newPayment(Jwt jwt, NewPaymentDto newPaymentDto);

    /**
     * Retrieves all transactions for a specific account of the authenticated user.
     *
     * @param jwt JWT token of the authenticated user
     * @param accountNumber account number whose transactions are being retrieved
     * @param page page number
     * @param size number of items per page
     * @return paginated list of transactions
     */
    Page<TransactionResponseDto> findAllTransactions(Jwt jwt, String accountNumber, int page, int size);

    /**
     * Retrieves transactions with advanced filtering by various criteria.
     *
     * @param jwt JWT token of the authenticated user
     * @param accountNumber account number (optional)
     * @param transactionStatus transaction status (optional)
     * @param fromDate start date (optional)
     * @param toDate end date (optional)
     * @param initialAmountMin minimum initial amount (optional)
     * @param initialAmountMax maximum initial amount (optional)
     * @param finalAmountMin minimum final amount (optional)
     * @param finalAmountMax maximum final amount (optional)
     * @param page page number
     * @param size number of items per page
     * @return filtered and paginated list of transactions
     */
    Page<TransactionResponseDto> findPayments(Jwt jwt, String accountNumber, TransactionStatus transactionStatus, LocalDateTime fromDate, LocalDateTime toDate, BigDecimal initialAmountMin, BigDecimal initialAmountMax, BigDecimal finalAmountMin, BigDecimal finalAmountMax, int page, int size);

    /**
     * Retrieves all transactions for a specific account (employee access - no owner restriction).
     *
     * @param accountNumber account number whose transactions are being retrieved
     * @param page page number
     * @param size number of items per page
     * @return paginated list of transactions
     */
    Page<TransactionResponseDto> findAllTransactionsForEmployee(String accountNumber, int page, int size);

}
