package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.Account;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.dto.request.TransactionDto;
import com.banka1.account_service.dto.response.UpdatedBalanceResponseDto;
import com.banka1.account_service.exception.BusinessException;
import com.banka1.account_service.exception.ErrorCode;
import com.banka1.account_service.repository.AccountRepository;
import com.banka1.account_service.service.TransactionalService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TransactionalServiceImplementation implements TransactionalService {
    private final AccountRepository accountRepository;
    private void validate(Account account)
    {
        //TODO prebaciti sve u business (svaki controller)
        if(account==null)
            throw new IllegalArgumentException("Ne postoji racun");
        if(account.getStatus()== Status.INACTIVE)
            throw new IllegalArgumentException("Racun je neaktivan");
        if(account.getDatumIsteka().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Racun je istekao");

    }
    @Transactional
    @Override
    public UpdatedBalanceResponseDto debit(Jwt jwt, String accountNumber, TransactionDto transactionDto) {
        Account account = accountRepository.findByBrojRacuna(accountNumber).orElse(null);
        validate(account);
        if(account.getRaspolozivoStanje().compareTo(transactionDto.getAmount())<0)
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS,ErrorCode.INSUFFICIENT_FUNDS.getTitle());
        if(account.getDnevnaPotrosnja().add(transactionDto.getAmount()).compareTo(account.getDnevniLimit())>0)
            throw new BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED,ErrorCode.DAILY_LIMIT_EXCEEDED.getTitle());
        if(account.getMesecnaPotrosnja().add(transactionDto.getAmount()).compareTo(account.getMesecniLimit())>0)
            throw new BusinessException(ErrorCode.MONTHLY_LIMIT_EXCEEDED,ErrorCode.MONTHLY_LIMIT_EXCEEDED.getTitle());
        account.setStanje(account.getStanje().subtract(transactionDto.getAmount()));
        account.setRaspolozivoStanje(account.getRaspolozivoStanje().subtract(transactionDto.getAmount()));
        account.setDnevnaPotrosnja(account.getDnevnaPotrosnja().add(transactionDto.getAmount()));
        account.setMesecnaPotrosnja(account.getMesecnaPotrosnja().add(transactionDto.getAmount()));
        accountRepository.saveAndFlush(account);
        return new UpdatedBalanceResponseDto(account.getStanje(), account.getRaspolozivoStanje(), account.getDnevnaPotrosnja(), account.getMesecnaPotrosnja());
    }
    @Transactional
    @Override
    public UpdatedBalanceResponseDto credit(Jwt jwt, String accountNumber, TransactionDto transactionDto) {
        Account account = accountRepository.findByBrojRacuna(accountNumber).orElse(null);
        validate(account);
        account.setStanje(account.getStanje().add(transactionDto.getAmount()));
        account.setRaspolozivoStanje(account.getRaspolozivoStanje().add(transactionDto.getAmount()));
        accountRepository.saveAndFlush(account);
        return new UpdatedBalanceResponseDto(account.getStanje(), account.getRaspolozivoStanje(), account.getDnevnaPotrosnja(), account.getMesecnaPotrosnja());
    }
}
