package com.banka1.transaction_service.service.implementation;

import com.banka1.transaction_service.domain.Payment;
import com.banka1.transaction_service.domain.enums.TransactionStatus;
import com.banka1.transaction_service.dto.request.ApproveDto;
import com.banka1.transaction_service.dto.request.NewPaymentDto;
import com.banka1.transaction_service.dto.request.PaymentDto;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.NoSuchElementException;

/**
 * Implementation of the service for managing transactions.
 * <p>
 * This class contains the complete business logic for creating, searching, and filtering transactions (payments),
 * including validations, communication with external services, and database operations.
 */
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
    @Value("${banka.security.roles-claim}")
    private String roles;
    @Value("${transaction.verification.skip:true}")
    private boolean skipVerification;
    private final TransactionServiceInternal transactionServiceInternal;
    private final PaymentRepository paymentRepository;

    /**
     * Creates a new transaction (payment) with complete business logic.
     * <p>
     * Process:
     * <ul>
     *   <li>Checks client verification (if enabled)</li>
     *   <li>Finds accounts and validates them</li>
     *   <li>Calculates currency conversion</li>
     *   <li>Creates Payment entity in the database</li>
     *   <li>Attempts to execute the transfer up to 3 times with retry logic</li>
     *   <li>Updates status and sends email notification</li>
     * </ul>
     *
     * @param jwt JWT token of the authenticated user
     * @param newPaymentDto DTO with new payment details
     * @return response with payment status and message
     */
    @Override
    public NewPaymentResponseDto newPayment(Jwt jwt, NewPaymentDto newPaymentDto) {
        if (!skipVerification) {
            VerificationStatusResponse verificationStatusResponse = verificationService.getStatus(newPaymentDto.getVerificationSessionId());
            if (verificationStatusResponse == null || !verificationStatusResponse.isVerified())
                throw new BusinessException(ErrorCode.VERIFICATION_FAILED, ErrorCode.VERIFICATION_FAILED.getTitle());
        } else {
            log.warn("SKIP_VERIFICATION=true, preskačem proveru verifikacionog koda");
        }
        InfoResponseDto infoResponseDto;
        try {
            infoResponseDto = accountService.getInfo(newPaymentDto.getFromAccountNumber(),newPaymentDto.getToAccountNumber());
        } catch (HttpClientErrorException ex) {
            if (isMissingAccountError(ex)) {
                throw new NoSuchElementException("Account number does not exist");
            }
            throw ex;
        }
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
            return new NewPaymentResponseDto("Uspesan payment", transactionStatus.name());
        return new NewPaymentResponseDto("Payment nije bio uspesan", transactionStatus.name());
    }

    /**
     * Checks if the HTTP error is due to a non-existent account.
     * Used to distinguish errors from the Account service.
     *
     * @param ex HTTP error from the Account service
     * @return true if the error is "account does not exist", false otherwise
     */
    private boolean isMissingAccountError(HttpClientErrorException ex) {
        if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
            return true;
        }

        if (!HttpStatus.BAD_REQUEST.equals(ex.getStatusCode())) {
            return false;
        }

        String body = ex.getResponseBodyAsString();
        if (body == null) {
            return false;
        }

        String normalized = body.toLowerCase(Locale.ROOT);
        return normalized.contains("ne postoji") && normalized.contains("racun");
    }

    /**
     * Retrieves all transactions for a specific client account with authentication.
     * <p>
     * Only the account owner can access this method.
     *
     * @param jwt JWT token of the authenticated user
     * @param accountNumber account number
     * @param page page number
     * @param size number of items per page
     * @return paginated list of transactions for the given account
     */
    @Transactional
    @Override
    public Page<TransactionResponseDto> findAllTransactions(Jwt jwt, String accountNumber, int page, int size) {
        if(jwt.getClaimAsString(roles).equalsIgnoreCase("CLIENT_BASIC")) {
            AccountDetailsResponseDto accountDetailsResponseDto = accountService.getDetails(accountNumber);
            if (accountDetailsResponseDto == null)
                throw new IllegalStateException("Sistemska greska");
            if (accountDetailsResponseDto.getVlasnik() == null || !accountDetailsResponseDto.getVlasnik().equals(((Number) jwt.getClaim(appPropertiesId)).longValue()))
                throw new IllegalArgumentException("Nisi vlasnik racuna");
        }
        return paymentRepository.findByAccountNumber(accountNumber, PageRequest.of(page,size)).map(TransactionResponseDto::new);
    }

    /**
     * Retrieves transactions with advanced filtering - available only to employees and owners.
     *
     * @param jwt JWT token
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
    @Override
    public Page<TransactionResponseDto> findPayments(Jwt jwt, String accountNumber, TransactionStatus transactionStatus, LocalDateTime fromDate, LocalDateTime toDate, BigDecimal initialAmountMin, BigDecimal initialAmountMax, BigDecimal finalAmountMin, BigDecimal finalAmountMax, int page, int size) {
        if(jwt.getClaimAsString(roles).equalsIgnoreCase("CLIENT_BASIC"))
        {
        AccountDetailsResponseDto accountDetailsResponseDto=accountService.getDetails(accountNumber);
        if(accountDetailsResponseDto == null)
            throw new IllegalStateException("Sistemska greska");
        if(accountDetailsResponseDto.getVlasnik()==null || !accountDetailsResponseDto.getVlasnik().equals(((Number) jwt.getClaim(appPropertiesId)).longValue()))
            throw new IllegalArgumentException("Nisi vlasnik racuna");
        }
        Specification<Payment> specification = Specification.unrestricted();

        if (accountNumber != null) {
            specification = specification.and((root, query, cb) ->
                    cb.or(
                            cb.equal(root.get("fromAccountNumber"), accountNumber),
                            cb.equal(root.get("toAccountNumber"), accountNumber)
                    ));
        }
        if (transactionStatus != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("status"), transactionStatus));
        }
        if (fromDate != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
        }
        if (toDate != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
        }
        if (initialAmountMin != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("initialAmount"), initialAmountMin));
        }
        if (initialAmountMax != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("initialAmount"), initialAmountMax));
        }
        if (finalAmountMin != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("finalAmount"), finalAmountMin));
        }
        if (finalAmountMax != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("finalAmount"), finalAmountMax));
        }

        return paymentRepository.findAll(
                        specification,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(TransactionResponseDto::new);
    }

    /**
     * Retrieves all transactions for a specific account - employee access without owner restrictions.
     *
     * @param accountNumber account number
     * @param page page number
     * @param size number of items per page
     * @return paginated list of transactions
     */
    @Transactional
    @Override
    public Page<TransactionResponseDto> findAllTransactionsForEmployee(String accountNumber, int page, int size) {
        return paymentRepository.findByAccountNumber(accountNumber, PageRequest.of(page, size))
                .map(TransactionResponseDto::new);
    }

    //todo for now leaving this here, validations should be a separate service, left ifs just in case
    //TODO change exceptions

//    private void  validation(AccountDto account,Jwt jwt)
//    {
//        if(account==null)
//            throw new IllegalArgumentException("Ne postoji unet racun");
//        if(!account.getVlasnik().equals(((Number) jwt.getClaim(appPropertiesId)).longValue()))
//            throw new IllegalArgumentException("Nisi vlasnik racuna");
//    }

}
