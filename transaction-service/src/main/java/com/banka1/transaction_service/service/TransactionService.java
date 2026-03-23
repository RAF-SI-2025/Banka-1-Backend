package com.banka1.transaction_service.service;

import com.banka1.transaction_service.domain.enums.TransactionStatus;
import com.banka1.transaction_service.dto.request.ApproveDto;
import com.banka1.transaction_service.dto.request.NewPaymentDto;
import com.banka1.transaction_service.dto.response.TransactionResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface TransactionService {
    String newPayment(Jwt jwt, NewPaymentDto newPaymentDto);
    String approveTransaction(Jwt jwt, Long id, ApproveDto newPaymentDto);
    Page<TransactionResponseDto> findAllTransactions(Jwt jwt, Long id, TransactionStatus transactionStatus, LocalDate fromDate, LocalDate toDate, BigDecimal minAmount, BigDecimal maxAmount, int page, int size);
}
