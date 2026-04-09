package com.banka1.transaction_service.service.implementation;

import com.banka1.transaction_service.domain.Payment;
import com.banka1.transaction_service.domain.enums.CurrencyCode;
import com.banka1.transaction_service.domain.enums.TransactionStatus;
import com.banka1.transaction_service.dto.request.NewPaymentDto;
import com.banka1.transaction_service.dto.response.ConversionResponseDto;
import com.banka1.transaction_service.dto.response.InfoResponseDto;
import com.banka1.transaction_service.dto.response.UpdatedBalanceResponseDto;
import com.banka1.transaction_service.rabbitMQ.EmailDto;
import com.banka1.transaction_service.rabbitMQ.EmailType;
import com.banka1.transaction_service.rabbitMQ.RabbitClient;
import com.banka1.transaction_service.repository.PaymentRepository;
import com.banka1.transaction_service.service.TransactionServiceInternal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * Implementation of the TransactionServiceInternal interface.
 * Provides internal methods for creating, finishing, and cleaning up transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Setter
@Getter
@Transactional
public class TransactionServiceInternalImplementation implements TransactionServiceInternal {
    private final PaymentRepository paymentRepository;
    private final RabbitClient rabbitClient;

    /**
     * Creates a new transaction in the database.
     *
     * @param jwt JWT token of the authenticated user
     * @param newPaymentDto DTO with new payment details
     * @param infoResponseDto Response DTO with account information
     * @param conversionResponseDto Response DTO with conversion details
     * @return the ID of the created transaction
     */
    @Override
    public Long create(Jwt jwt, NewPaymentDto newPaymentDto, InfoResponseDto infoResponseDto, ConversionResponseDto conversionResponseDto) {
        Payment payment=new Payment();
        payment.setFromAccountNumber(newPaymentDto.getFromAccountNumber());
        payment.setToAccountNumber(newPaymentDto.getToAccountNumber());
        payment.setStatus(TransactionStatus.IN_PROGRESS);
        payment.setInitialAmount(newPaymentDto.getAmount());
        payment.setRecipientClientId(infoResponseDto.getToVlasnik());
        payment.setFinalAmount(conversionResponseDto.toAmount());
        payment.setCommission(conversionResponseDto.commission());
        payment.setRecipientName(newPaymentDto.getRecipientName());
        payment.setPaymentCode(newPaymentDto.getPaymentCode());
        payment.setReferenceNumber(newPaymentDto.getReferenceNumber());
        payment.setPaymentPurpose(newPaymentDto.getPaymentPurpose());
        payment.setFromCurrency(CurrencyCode.valueOf(conversionResponseDto.fromCurrency().toUpperCase()));
        payment.setToCurrency(CurrencyCode.valueOf(conversionResponseDto.toCurrency().toUpperCase()));
        payment.setExchangeRate(conversionResponseDto.rate());
        payment=paymentRepository.save(payment);
        payment.setOrderNumber("BANKA1-"+payment.getId());
        return payment.getId();
    }

    /**
     * Finalizes a transaction by updating its status and sending notifications.
     *
     * @param jwt JWT token of the authenticated user
     * @param infoResponseDto Response DTO with account information
     * @param id ID of the transaction to finalize
     * @param transactionStatus Final status of the transaction
     */
    @Override
    public void finish(Jwt jwt, InfoResponseDto infoResponseDto, Long id, TransactionStatus transactionStatus) {
        Payment payment=paymentRepository.findById(id).orElseThrow(()->new IllegalStateException("Greska u sistemu, nije sacuvao entitet"));
        payment.setStatus(transactionStatus);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitClient.sendEmailNotification(new EmailDto(infoResponseDto.getFromUsername(),infoResponseDto.getFromEmail(), (transactionStatus==TransactionStatus.COMPLETED)?EmailType.TRANSACTION_COMPLETED:EmailType.TRANSACTION_DENIED));

            }
        });

    }

    /**
     * Cleans up old transactions from the database.
     * Removes transactions that are older than a specified threshold.
     */
    @Scheduled(fixedRate = 100000)
    public void cleanup() {
        int updated = paymentRepository.markStuckPayments(
                TransactionStatus.IN_PROGRESS,
                TransactionStatus.DENIED,
                LocalDateTime.now().minusMinutes(5)
        );

        if (updated > 0) {
            log.warn("Marked {} stuck payments as DENIED", updated);
        }
    }


}
