package com.banka1.transfer.service.impl;

import com.banka1.transfer.client.AccountClient;
import com.banka1.transfer.client.ClientClient;
import com.banka1.transfer.client.ExchangeClient;
import com.banka1.transfer.client.VerificationClient;
import com.banka1.transfer.domain.Transfer;
import com.banka1.transfer.dto.client.ClientInfoResponseDto;
import com.banka1.transfer.dto.client.PaymentDto;
import com.banka1.transfer.dto.requests.TransferRequestDto;
import com.banka1.transfer.dto.responses.TransferResponseDto;
import com.banka1.transfer.exception.BusinessException;
import com.banka1.transfer.exception.ErrorCode;
import com.banka1.transfer.mapper.TransferMapper;
import com.banka1.transfer.rabbitmq.EmailDto;
import com.banka1.transfer.rabbitmq.EmailType;
import com.banka1.transfer.rabbitmq.RabbitClient;
import com.banka1.transfer.repository.TransferRepository;
import com.banka1.transfer.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Implementacija {@link TransferService} koja orkestrira interakciju između više mikroservisa.
 * Servis osigurava atomičnost transakcije, vrši idempotenciju putem verifikacione sesije,
 * upravlja konverzijom valuta putem menjačnice i inicira asinhrono slanje notifikacija.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl implements TransferService {

    private final TransferRepository transferRepository;
    private final TransferMapper transferMapper;

    private final AccountClient accountClient;
    private final ExchangeClient exchangeClient;
    private final VerificationClient verificationClient;
    private final RabbitClient rabbitClient;
    private final ClientClient clientClient;

    @Value("${transfer.verification.skip:true}")
    private boolean skipVerification;

    /**
     * Glavna metoda za izvršavanje transfera.
     * Obavlja validaciju sesije, proverava vlasništvo računa u Account servisu,
     * vrši konverziju u Exchange servisu i na kraju beleži transfer u lokalnu bazu.
     * @param jwt Token koji služi za autentifikaciju i autorizaciju korisnika.
     * @param request Detalji transfera.
     * @return DTO sa informacijama o uspehu i broju naloga.
     */
    @Transactional
    @Override
    public TransferResponseDto executeTransfer(Jwt jwt, TransferRequestDto request) {
        String fromAccountNumber = request.getFromAccountNumber();
        String toAccountNumber = request.getToAccountNumber();
        Long verificationSessionId = request.getVerificationSessionId();

        log.info("Processing transfer request from {}", fromAccountNumber);

        // Idempotency Check
        if (transferRepository.existsByVerificationSessionId(verificationSessionId.toString())) {
            throw new BusinessException(ErrorCode.TRANSFER_ALREADY_PROCESSED, "Ovaj transfer je već realizovan.");
        }

        // Osnovna logička validacija
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new BusinessException(ErrorCode.SAME_ACCOUNT_TRANSFER, "Ne možete prebaciti novac na isti račun sa kog šaljete.");
        }

        if (!skipVerification) {
            var verifyRes = verificationClient.getVerificationStatus(verificationSessionId);
            if (!verifyRes.isVerified()) {
                throw new BusinessException(ErrorCode.INVALID_VERIFICATION, "Verifikacija nije uspela. Trenutni status sesije: " + verifyRes.status());
            }
        } else {
            log.warn("MOCK: Preskačem proveru verifikacionog koda jer je SKIP_VERIFICATION=true");
        }

        // Dohvatanje meta-podataka (Account Service)
        var fromAcc = accountClient.getAccountDetails(fromAccountNumber);
        var toAcc = accountClient.getAccountDetails(toAccountNumber);

        if (fromAcc == null || toAcc == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Račun nije pronađen.");
        }

        Long loggedInId = getUserIdFromJwt(jwt);
        if (!isPrivilegedUser(jwt) && !fromAcc.ownerId().equals(loggedInId)) {
            throw new BusinessException(ErrorCode.ACCOUNT_OWNERSHIP_MISMATCH, "Ne možete inicirati transfer sa tuđeg računa.");
        }

        // Biznis validacija: Da li oba računa pripadaju istom klijentu?
        if (!fromAcc.ownerId().equals(toAcc.ownerId())) {
            throw new BusinessException(ErrorCode.ACCOUNT_OWNERSHIP_MISMATCH, "Transfer je dozvoljen samo između računa istog vlasnika.");
        }

        BigDecimal finalAmount = request.getAmount();
        BigDecimal exchangeRate = null;
        BigDecimal commission = BigDecimal.ZERO;

        // Kalkulacija Menjačnice (ako su valute različite)
        if (!fromAcc.currency().equals(toAcc.currency())) {
            var exchange = exchangeClient.calculateExchange(fromAcc.currency(), toAcc.currency(), request.getAmount());
            finalAmount = exchange.toAmount();
            exchangeRate = exchange.rate();
            commission = exchange.commission();
        }

        // Kreiranje PaymentDto za Account Service
        PaymentDto paymentDto = new PaymentDto(
                fromAcc.accountNumber(),
                toAcc.accountNumber(),
                request.getAmount(), // fromAmount
                finalAmount,         // toAmount
                commission,
                fromAcc.ownerId()    // Prosleđujemo clientId zbog account-service validacije
        );

        String orderNumber = generateOrderNumber();

        // Ako ovo padne (npr. nedovoljno para), baciće exception, @Transactional će odraditi rollback i kod se prekida.
        accountClient.executeTransfer(paymentDto);

        // Čuvanje zapisa u našu bazu
        Transfer transfer = transferMapper.toEntity(
                request,
                orderNumber,
                fromAcc.ownerId(),
                finalAmount,
                exchangeRate,
                commission
        );
        Transfer savedTransfer = transferRepository.save(transfer);
        // Slanje notifikacije (Asinhrono, NAKON uspešnog upisa u DB)

        ClientInfoResponseDto clientInfo = clientClient.getClientDetails(fromAcc.ownerId());

        EmailDto emailDto = new EmailDto(
                clientInfo.getName(),
                clientInfo.getEmail(),
                EmailType.TRANSFER_COMPLETED,
                "Uspešno ste izvršili prenos sredstava. Broj naloga: " + orderNumber
        );

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitClient.sendEmailNotification(emailDto);
                log.info("Sent transfer completion email for order {}", orderNumber);
            }
        });

        return transferMapper.toDto(savedTransfer);
    }

    /**
     * Generiše jedinstveni, ljudski čitljiv broj naloga za svaku transakciju.
     * Format: TRF - [UUID sufiks] - [Timestamp].
     */
    private String generateOrderNumber() {
        return "TRF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "-" + Instant.now().toEpochMilli();
    }

    /**
     * Pretražuje bazu za sve transfere određenog klijenta.
     */
    @Override
    public Page<TransferResponseDto> getClientTransfers(Long clientId, Pageable pageable) {
        return transferRepository.findByClientId(clientId, pageable)
                .map(transferMapper::toDto); // Korišćenje mapplera
    }

    /**
     * Pretražuje bazu za detalje specifičnog naloga.
     * Baca {@link BusinessException} ako nalog ne postoji.
     */
    @Override
    public TransferResponseDto getTransferDetails(Jwt jwt, String orderNumber) {
        Transfer transfer = transferRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND, "Transfer sa brojem " + orderNumber + " ne postoji."));

        if (!isPrivilegedUser(jwt) && !transfer.getClientId().equals(getUserIdFromJwt(jwt))) {
            throw new BusinessException(ErrorCode.ACCOUNT_OWNERSHIP_MISMATCH, "Nemate prava da pregledate ovaj transfer.");
        }

        return transferMapper.toDto(transfer); // Korišćenje mapplera
    }

    /**
     * Pretražuje bazu za sve transakcije (uplate/isplate) vezane za jedan račun.
     */
    @Override
    public Page<TransferResponseDto> getTransfersByAccountNumber(Jwt jwt, String accountNumber, Pageable pageable) {
        var accDetails = accountClient.getAccountDetails(accountNumber);
        if (accDetails == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Račun nije pronađen.");
        }

        if (!isPrivilegedUser(jwt) && !accDetails.ownerId().equals(getUserIdFromJwt(jwt))) {
            throw new BusinessException(ErrorCode.ACCOUNT_OWNERSHIP_MISMATCH, "Nemate prava da listate transfere tuđeg računa.");
        }

        return transferRepository.findByFromAccountNumberOrToAccountNumber(accountNumber, accountNumber, pageable).map(transferMapper::toDto);
    }

    private Long getUserIdFromJwt(Jwt jwt) {
        return Long.parseLong(jwt.getClaimAsString("id"));
    }

    private boolean isPrivilegedUser(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) return false;
        // Proverava da li korisnik ima ADMIN ili SERVICE rolu
        return roles.stream().anyMatch(r -> r.contains("ADMIN") || r.contains("SERVICE"));
    }
}