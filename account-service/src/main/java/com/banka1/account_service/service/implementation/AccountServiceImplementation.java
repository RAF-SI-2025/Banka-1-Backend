package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.Account;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.dto.request.CheckingDto;
import com.banka1.account_service.dto.request.TransactionDto;
import com.banka1.account_service.dto.response.UpdatedBalanceResponseDto;
import com.banka1.account_service.exception.BusinessException;
import com.banka1.account_service.exception.ErrorCode;
import com.banka1.account_service.repository.AccountRepository;
import com.banka1.account_service.service.AccountService;
import com.banka1.account_service.service.TransactionalService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Service
public class AccountServiceImplementation implements AccountService {
    private final TransactionalService transactionalService;


    @Override
    public UpdatedBalanceResponseDto debit(Jwt jwt, String accountNumber, TransactionDto transactionDto) {
        for(int i = 0; true; i++) {
            try {
                return transactionalService.debit(jwt,accountNumber,transactionDto);
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException optimisticLockException) {
                if(i>=3)
                    throw optimisticLockException;
            }
        }
    }
    @Override
    public UpdatedBalanceResponseDto credit(Jwt jwt, String accountNumber, TransactionDto transactionDto) {
        for(int i = 0; true; i++) {
            try {
                return transactionalService.credit(jwt,accountNumber, transactionDto);
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException optimisticLockException) {
                if(i>=3)
                    throw optimisticLockException;
            }
        }
    }
}
