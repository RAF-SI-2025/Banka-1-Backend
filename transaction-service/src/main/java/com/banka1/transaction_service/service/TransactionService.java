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

public interface TransactionService {
    NewPaymentResponseDto newPayment(Jwt jwt, NewPaymentDto newPaymentDto);
    Page<TransactionResponseDto> findAllTransactions(Jwt jwt, String accountNumber, int page, int size);
    Page<TransactionResponseDto> findPayments(Jwt jwt, String accountNumber, TransactionStatus transactionStatus, LocalDateTime fromDate, LocalDateTime toDate, BigDecimal initialAmountMin, BigDecimal initialAmountMax, BigDecimal finalAmountMin, BigDecimal finalAmountMax, int page, int size);
    Page<TransactionResponseDto> findAllTransactionsForEmployee(String accountNumber, int page, int size);

}
