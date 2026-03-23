package com.banka1.account_service.service;

import com.banka1.account_service.dto.request.TransactionDto;
import com.banka1.account_service.dto.response.UpdatedBalanceResponseDto;
import org.springframework.security.oauth2.jwt.Jwt;

public interface TransactionalService {
    UpdatedBalanceResponseDto debit(Jwt jwt, String accountNumber, TransactionDto transactionDto);
    UpdatedBalanceResponseDto credit( Jwt jwt, String accountNumber, TransactionDto transactionDto);
}
