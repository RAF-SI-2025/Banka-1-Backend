package com.banka1.transaction_service.service.implementation;

import com.banka1.transaction_service.domain.enums.TransactionStatus;
import com.banka1.transaction_service.domain.enums.VerificationStatus;
import com.banka1.transaction_service.dto.request.ApproveDto;
import com.banka1.transaction_service.dto.request.NewPaymentDto;
import com.banka1.transaction_service.dto.request.PaymentDto;
import com.banka1.transaction_service.dto.request.ValidateRequest;
import com.banka1.transaction_service.dto.response.*;
import com.banka1.transaction_service.exception.BusinessException;
import com.banka1.transaction_service.exception.ErrorCode;
import com.banka1.transaction_service.rabbitMQ.EmailDto;
import com.banka1.transaction_service.rabbitMQ.EmailType;
import com.banka1.transaction_service.rabbitMQ.RabbitClient;
import com.banka1.transaction_service.repository.PaymentRepository;
import com.banka1.transaction_service.rest_client.AccountService;
import com.banka1.transaction_service.rest_client.ClientService;
import com.banka1.transaction_service.rest_client.ExchangeService;
import com.banka1.transaction_service.rest_client.VerificationService;
import com.banka1.transaction_service.service.TransactionService;
import com.banka1.transaction_service.service.TransactionServiceInternal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Getter
@Setter
@Service
@Slf4j
public class TransactionServiceImplementation implements TransactionService {

    private final ExchangeService exchangeService;
    private final VerificationService verificationService;
    private final AccountService accountService;
    private final ClientService clientService;
    @Value("${banka.security.id}")
    private String appPropertiesId;
    @Value("banka.security.roles-claim")
    private String roles;
    private final TransactionServiceInternal transactionServiceInternal;
    private final PaymentRepository paymentRepository;



//    @Transactional
    @Override
    public String newPayment(Jwt jwt, NewPaymentDto newPaymentDto) {
        ValidateResponse validateResponse= verificationService.validate(new ValidateRequest(newPaymentDto.getVerificationSessionId(),newPaymentDto.getVerificationCode()));
        if(validateResponse==null || validateResponse.getStatus()!= VerificationStatus.VERIFIED)
            throw new BusinessException(ErrorCode.VERIFICATION_FAILED,ErrorCode.VERIFICATION_FAILED.getTitle());
        InfoResponseDto infoResponseDto=accountService.getInfo(newPaymentDto.getFromAccountNumber(),newPaymentDto.getToAccountNumber());
        if(infoResponseDto == null)
            throw new IllegalStateException("Greska sa account servisom");
        ConversionResponseDto conversionResponseDto=exchangeService.calculate(infoResponseDto.getFromCurrencyCode(),infoResponseDto.getToCurrencyCode(),newPaymentDto.getAmount());
        if(conversionResponseDto == null)
            throw new IllegalStateException("Greska sa account servisom");
        Long id=transactionServiceInternal.create(jwt,newPaymentDto,infoResponseDto,conversionResponseDto);
        UpdatedBalanceResponseDto updatedBalanceResponseDto=null;
        TransactionStatus transactionStatus = TransactionStatus.DENIED;
        PaymentDto paymentDto = new PaymentDto(newPaymentDto.getFromAccountNumber(), newPaymentDto.getToAccountNumber(), conversionResponseDto.fromAmount(), conversionResponseDto.toAmount(), conversionResponseDto.commission(), ((Number) jwt.getClaim(appPropertiesId)).longValue());
        boolean sameOwner = infoResponseDto.getFromVlasnik().equals(infoResponseDto.getToVlasnik());
        for(int i=0;i<3;i++) {
            try {
                if (sameOwner) {
                    updatedBalanceResponseDto = accountService.transfer(paymentDto);
                } else {
                    updatedBalanceResponseDto = accountService.transaction(paymentDto);
                }
                transactionStatus=TransactionStatus.COMPLETED;
                break;
            } catch (RestClientException e) {
                log.warn("Transfer failed attempt {}", i, e);
            }
        }
        transactionServiceInternal.finish(jwt,infoResponseDto,id, transactionStatus);
        if(transactionStatus==TransactionStatus.COMPLETED)
            return "Uspesan payment";
        return "Payment nije bio uspesan";
    }

    @Transactional
    @Override
    public Page<TransactionResponseDto> findAllTransactions(Jwt jwt, String accountNumber, int page, int size) {
        AccountDetailsResponseDto accountDetailsResponseDto=accountService.getDetails(accountNumber);
        if(accountDetailsResponseDto == null)
            throw new IllegalStateException("Sistemska greska");
        if(accountDetailsResponseDto.getVlasnik()==null || !accountDetailsResponseDto.getVlasnik().equals(((Number) jwt.getClaim(appPropertiesId)).longValue()))
            throw new IllegalArgumentException("Nisi vlasnik racuna");
        return paymentRepository.findByAccountNumber(accountNumber, PageRequest.of(page,size)).map(TransactionResponseDto::new);
    }

    @Override
    public Page<TransactionResponseDto> findPayments(Jwt jwt, String accountNumber, TransactionStatus transactionStatus, LocalDateTime fromDate, LocalDateTime toDate, BigDecimal initialAmountMin, BigDecimal initialAmountMax, BigDecimal finalAmountMin, BigDecimal finalAmountMax, int page, int size) {
        if(!jwt.getClaimAsString(roles).equalsIgnoreCase("ADMIN"))
        {
        AccountDetailsResponseDto accountDetailsResponseDto=accountService.getDetails(accountNumber);
        if(accountDetailsResponseDto == null)
            throw new IllegalStateException("Sistemska greska");
        if(accountDetailsResponseDto.getVlasnik()==null || !accountDetailsResponseDto.getVlasnik().equals(((Number) jwt.getClaim(appPropertiesId)).longValue()))
            throw new IllegalArgumentException("Nisi vlasnik racuna");
        }
        return paymentRepository.searchPayments(accountNumber,transactionStatus,fromDate,toDate,initialAmountMin,initialAmountMax,finalAmountMin,finalAmountMax, PageRequest.of(page,size)).map(TransactionResponseDto::new);
    }


    //todo za sad ovo ostavljam ovde, validacije bi trebalo da budu zaseban servis, if-ove sam ostavio just in case
    //TODO menjati exceptione

//    private void  validation(AccountDto account,Jwt jwt)
//    {
//        if(account==null)
//            throw new IllegalArgumentException("Ne postoji unet racun");
//        if(!account.getVlasnik().equals(((Number) jwt.getClaim(appPropertiesId)).longValue()))
//            throw new IllegalArgumentException("Nisi vlasnik racuna");
//    }





}
