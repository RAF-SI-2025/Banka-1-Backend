package com.banka1.account_service.service;

import com.banka1.account_service.domain.Account;
import com.banka1.account_service.dto.request.PaymentDto;
import com.banka1.account_service.dto.response.UpdatedBalanceResponseDto;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;

public interface TransactionalService {
    UpdatedBalanceResponseDto transfer(Account from, Account to,Account bankSender,Account bankTarget,PaymentDto paymentDto);
}
