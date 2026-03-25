package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.Account;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.dto.request.PaymentDto;
import com.banka1.account_service.dto.response.UpdatedBalanceResponseDto;
import com.banka1.account_service.repository.AccountRepository;
import com.banka1.account_service.service.AccountService;
import com.banka1.account_service.service.TransactionalService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@RequiredArgsConstructor
@Service
public class AccountServiceImplementation implements AccountService {
    private final TransactionalService transactionalService;
    private final AccountRepository accountRepository;

    private Account validate(String accountNumber)
    {
        Account account = accountRepository.findByBrojRacuna(accountNumber).orElse(null);
        //TODO prebaciti sve u business (svaki controller)
        if(account==null)
            throw new IllegalArgumentException("Ne postoji racun:"+accountNumber);
        if(account.getStatus()== Status.INACTIVE)
            throw new IllegalArgumentException("Racun je neaktivan:"+accountNumber);
        if(account.getDatumIsteka()!=null&&account.getDatumIsteka().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Racun je istekao:"+accountNumber);
        return account;
    }
    private Account validateBank(Account to)
    {
        Account account=accountRepository.findByIdAndCurrency((long) -1,to.getCurrency()).orElse(null);
        if(account==null)
            throw new IllegalStateException("Greska u sistemu fali banka");
        if(account.getStatus()== Status.INACTIVE)
            throw new IllegalStateException("Racun banke je neaktivan");
        if(account.getDatumIsteka()!=null&&account.getDatumIsteka().isBefore(LocalDate.now()))
            throw new IllegalStateException("Racun banke je istekao");
        return account;
    }


    //todo potencijalno migrirati na Springov Retry, za sad ne treba ali moze biti lepse
    private UpdatedBalanceResponseDto execute(PaymentDto paymentDto, Account from, Account to, Account bankSender, Account bankTarget) {
        if(!from.getVlasnik().equals(paymentDto.getClientId()))
            throw new IllegalArgumentException("Nisi vlasnik racuna");
        for(int i = 0; true; i++) {
            try {
                return transactionalService.transfer(from,to,bankSender,bankTarget,paymentDto);
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException optimisticLockException) {
                if(i>=2)
                    throw optimisticLockException;
            }
        }
    }

    @Override
    public UpdatedBalanceResponseDto transaction(PaymentDto paymentDto) {
        Account from=validate(paymentDto.getFromAccountNumber());
        Account to=validate(paymentDto.getToAccountNumber());
        Account bankSender=validateBank(from);
        Account bankTarget=validateBank(to);
        if(from.getVlasnik().equals(to.getVlasnik()))
            throw new IllegalArgumentException("Tranzakcija se ne moze odvijati za racune istog vlasnike");
        return execute(paymentDto, from, to, bankSender, bankTarget);
    }



    @Override
    public UpdatedBalanceResponseDto transfer(PaymentDto paymentDto) {
        Account from=validate(paymentDto.getFromAccountNumber());
        Account to=validate(paymentDto.getToAccountNumber());
        Account bankSender=validateBank(from);
        Account bankTarget=validateBank(to);
        if(paymentDto.getClientId()==null)
            throw new IllegalArgumentException("Unesi id clienta");
        if(!from.getVlasnik().equals(to.getVlasnik()))
            throw new IllegalArgumentException("Transfer se moze odvijati samo za racune istog vlasnika");
        return execute(paymentDto, from, to, bankSender, bankTarget);
    }
}
