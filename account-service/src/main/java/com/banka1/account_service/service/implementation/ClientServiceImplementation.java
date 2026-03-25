package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.Account;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.domain.enums.VerificationStatus;
import com.banka1.account_service.dto.request.EditAccountLimitDto;
import com.banka1.account_service.dto.request.EditAccountNameDto;
import com.banka1.account_service.dto.request.EditStatus;
import com.banka1.account_service.dto.request.ValidateRequest;
import com.banka1.account_service.dto.response.*;
import com.banka1.account_service.exception.BusinessException;
import com.banka1.account_service.exception.ErrorCode;
import com.banka1.account_service.rabbitMQ.EmailDto;
import com.banka1.account_service.rabbitMQ.EmailType;
import com.banka1.account_service.rabbitMQ.RabbitClient;
import com.banka1.account_service.repository.AccountRepository;
import com.banka1.account_service.rest_client.VerificationService;
import com.banka1.account_service.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class ClientServiceImplementation implements ClientService {
    private final AccountRepository accountRepository;
    @Value("${banka.security.id}")
    private String appPropertiesId;

    private final VerificationService verificationService;
    private final RabbitClient rabbitClient;


//    @Override
//    public String newPayment(Jwt jwt, NewPaymentDto newPaymentDto) {
//        return "";
//    }
//
//    //todo kada dodje mobile
//    @Override
//    public String approveTransaction(Jwt jwt, Long id, ApproveDto newPaymentDto) {
//        return "";
//    }


    @Transactional
    @Override
    public Page<AccountResponseDto> findMyAccounts(Jwt jwt, int page, int size) {
        return accountRepository.findByVlasnikAndStatus(((Number) jwt.getClaim(appPropertiesId)).longValue(), Status.ACTIVE,PageRequest.of(page, size)).map(AccountResponseDto::new);
    }

//    @Transactional
//    @Override
//    public Page<TransactionResponseDto> findAllTransactions(Jwt jwt, Long id, int page, int size) {
//        Account account=accountRepository.findById(id).orElse(null);
//        if(account==null)
//            throw new IllegalArgumentException("Ne postoji unet racun");
//        if(!account.getVlasnik().equals(((Number) jwt.getClaim(appPropertiesId)).longValue()))
//            throw new IllegalArgumentException("Nisi vlasnik racuna");
//
//        return null;
//    }

    //TODO menjati exceptione
    private void  validation(Account account,Jwt jwt)
    {
        if(account==null)
            throw new IllegalArgumentException("Ne postoji unet racun");
        if(!account.getVlasnik().equals(((Number) jwt.getClaim(appPropertiesId)).longValue()))
            throw new IllegalArgumentException("Nisi vlasnik racuna");
    }

    @Transactional
    @Override
    public String editAccountName(Jwt jwt, Long id, EditAccountNameDto editAccountNameDto) {
        Account account=accountRepository.findById(id).orElse(null);
        return editName(jwt, editAccountNameDto, account);
    }

    @Override
    public String editAccountName(Jwt jwt, String accountNumber, EditAccountNameDto editAccountNameDto) {
        Account account=accountRepository.findByBrojRacuna(accountNumber).orElse(null);
        return editName(jwt, editAccountNameDto, account);
    }

    @NonNull
    private String editName(Jwt jwt, EditAccountNameDto editAccountNameDto, Account account) {
        validation(account,jwt);
        if(account.getNazivRacuna().equalsIgnoreCase(editAccountNameDto.getAccountName()))
            throw new IllegalArgumentException("Ime ne sme biti isto");
        if(accountRepository.existsByVlasnikAndNazivRacuna(account.getVlasnik(),editAccountNameDto.getAccountName()))
            throw new IllegalArgumentException("Vlasnik poseduje racun sa ovim imenom");
        account.setNazivRacuna(editAccountNameDto.getAccountName());
        return "Uspesno editovano ime";
    }

    @Transactional
    @Override
    public String editAccountLimit(Jwt jwt, Long id, EditAccountLimitDto editAccountLimitDto) {
        Account account=accountRepository.findById(id).orElse(null);
        return editLimit(jwt, editAccountLimitDto, account);
    }

    @Override
    public String editAccountLimit(Jwt jwt, String accountNumber, EditAccountLimitDto editAccountLimitDto) {
        Account account=accountRepository.findByBrojRacuna(accountNumber).orElse(null);
        return editLimit(jwt, editAccountLimitDto, account);
    }

    @NonNull
    private String editLimit(Jwt jwt, EditAccountLimitDto editAccountLimitDto, Account account) {
        validation(account,jwt);

        if(editAccountLimitDto.getTipLimita() == EditAccountLimitDto.TipLimita.DNEVNI) {
            if(editAccountLimitDto.getAccountLimit().compareTo(account.getMesecniLimit())>0)
                throw new IllegalArgumentException("Dnevni limit mora biti manji ili jednak od mesecnog");
        }
        else
        {
            if(editAccountLimitDto.getAccountLimit().compareTo(account.getDnevniLimit())<0)
                throw new IllegalArgumentException("Mesecni limit mora biti veci ili jednak od dnevnog");
        }

        ValidateResponse validateResponse=verificationService.validate(new ValidateRequest(editAccountLimitDto.getVerificationSessionId(),editAccountLimitDto.getVerificationCode()));
        if(validateResponse.getStatus()!= VerificationStatus.VERIFIED)
            throw new BusinessException(ErrorCode.VERIFICATION_FAILED,ErrorCode.VERIFICATION_FAILED.getTitle());
        if(editAccountLimitDto.getTipLimita() == EditAccountLimitDto.TipLimita.DNEVNI) {
            account.setDnevniLimit(editAccountLimitDto.getAccountLimit());
        }
        else
        {
            account.setMesecniLimit(editAccountLimitDto.getAccountLimit());
        }


        return "Uspesno setovan limit";
    }

    @Transactional
    @Override
    public AccountDetailsResponseDto getDetails(Jwt jwt, Long id) {
        Account account=accountRepository.findById(id).orElse(null);
        validation(account,jwt);
        return new AccountDetailsResponseDto(account);
    }

    @Override
    public AccountDetailsResponseDto getDetails(Jwt jwt, String accountNumber) {
        Account account=accountRepository.findByBrojRacuna(accountNumber).orElse(null);
        validation(account,jwt);
        return new AccountDetailsResponseDto(account);
    }

    @Override
    public Page<CardResponseDto> findAllCards(Jwt jwt, Long id, int page, int size) {
        return null;
    }

    //todo dosta gluposti u vezi ovoga sto se tice Card i Loan servica
    @Override
    public String editStatus(Jwt jwt, String accountNumber, EditStatus editStatus) {
        Account account=accountRepository.findByBrojRacuna(accountNumber).orElse(null);
        validation(account,jwt);
        account.setStatus(editStatus.getStatus());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                //rabbitClient.sendEmailNotification(new EmailDto(clientInfoResponseDto.getUsername(),clientInfoResponseDto.getEmail(), EmailType.ACCOUNT_CREATED));

            }
        });
        return "Uspesno editovan status";
    }
}
