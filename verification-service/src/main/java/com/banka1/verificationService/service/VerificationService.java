package com.banka1.verificationService.service;

import com.banka1.verificationService.dto.event.VerificationGeneratedEvent;
import com.banka1.verificationService.dto.request.GenerateRequest;
import com.banka1.verificationService.dto.request.ValidateRequest;
import com.banka1.verificationService.dto.response.GenerateResponse;
import com.banka1.verificationService.dto.response.ValidateResponse;
import com.banka1.verificationService.exception.BusinessException;
import com.banka1.verificationService.exception.ErrorCode;
import com.banka1.verificationService.model.entity.VerificationSession;
import com.banka1.verificationService.model.enums.VerificationStatus;
import com.banka1.verificationService.repository.VerificationSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Servis za upravljanje sesijama verifikacije, uključujući generisanje, validaciju i proveru statusa.
 * Rukuje 2FA verifikacionim kodovima za operacije klijenata kao što su plaćanja, transferi i promene limita.
 */
@Service
@RequiredArgsConstructor
public class VerificationService {

    /** Repozitorijum za pristup podacima verifikacionih sesija iz baze podataka. */
    private final VerificationSessionRepository repository;

    /** Enkoder za heširanje verifikacionih kodova pre čuvanja. */
    private final PasswordEncoder passwordEncoder;

    /** Šablon za slanje poruka RabbitMQ-u za isporuku notifikacija. */
    private final RabbitTemplate rabbitTemplate;

    /** Naziv RabbitMQ exchange-a za objavljivanje događaja verifikacije. */
    @Value("${rabbitmq.exchange}")
    private String exchange;

    /** RabbitMQ routing ključ za usmeravanje događaja verifikacije ka servisu notifikacija. */
    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    /**
     * Generiše novu sesiju verifikacije sa nasumičnim 6-cifrenim kodom, hešira ga, čuva u bazi podataka,
     * i objavljuje događaj ka servisu notifikacija za isporuku klijentu.
     *
     * @param request sadrži ID klijenta, tip operacije i ID povezanog entiteta
     * @return odgovor sa ID-om generisane sesije
     */
    @Transactional
    public GenerateResponse generate(GenerateRequest request) {
        String rawCode = generateCode();
        String hashedCode = passwordEncoder.encode(rawCode);

        LocalDateTime now = LocalDateTime.now();

        VerificationSession session = VerificationSession.builder()
                .clientId(request.getClientId())
                .code(hashedCode)
                .operationType(request.getOperationType())
                .relatedEntityId(request.getRelatedEntityId())
                .createdAt(now)
                .expiresAt(now.plusMinutes(5))
                .attemptCount(0)
                .status(VerificationStatus.PENDING)
                .build();

        repository.save(session);

        // Publish event to notification-service
        VerificationGeneratedEvent event = new VerificationGeneratedEvent();
        event.setClientId(request.getClientId());
        event.setCode(rawCode);
        event.setOperationType(request.getOperationType());
        rabbitTemplate.convertAndSend(exchange, routingKey, event);

        return new GenerateResponse(session.getId());
    }

    /**
     * Validira dati kod u odnosu na sačuvani heširani kod za sesiju.
     * Proverava status sesije, isticanje i broj pokušaja. Ažurira status shodno tome.
     *
     * @param request sadrži ID sesije i kod za validaciju
     * @return odgovor koji ukazuje na rezultat validacije, trenutni status i preostale pokušaje
     */
    @Transactional
    public ValidateResponse validate(ValidateRequest request) {
        VerificationSession session = repository.findById(request.getSessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_SESSION_NOT_FOUND, "Session ID: " + request.getSessionId()));

        // 1. status check
        if (session.getStatus() == VerificationStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.VERIFICATION_SESSION_CANCELLED, "Session ID: " + request.getSessionId());
        }

        if (session.getStatus() == VerificationStatus.VERIFIED) {
            throw new BusinessException(ErrorCode.VERIFICATION_SESSION_ALREADY_VERIFIED, "Session ID: " + request.getSessionId());
        }

        // 2. expiration check
        if (LocalDateTime.now().isAfter(session.getExpiresAt())) {
            session.setStatus(VerificationStatus.EXPIRED);
            repository.save(session);
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED, "Session ID: " + request.getSessionId());
        }

        // 3. validate code
        boolean matches = passwordEncoder.matches(request.getCode(), session.getCode());

        if (matches) {
            session.setStatus(VerificationStatus.VERIFIED);
            repository.save(session);

            return new ValidateResponse(true, session.getStatus(), 0);
        }

        // 4. wrong code
        session.setAttemptCount(session.getAttemptCount() + 1);

        if (session.getAttemptCount() >= 3) {
            session.setStatus(VerificationStatus.CANCELLED);
        }

        repository.save(session);

        int remaining = 3 - session.getAttemptCount();

        return new ValidateResponse(false, session.getStatus(), remaining);
    }

    /**
     * Vraća trenutni status verifikacione sesije, sa opcionom automatskom proverom isticanja.
     *
     * @param sessionId ID sesije za proveru
     * @return trenutni status verifikacije
     */
    @Transactional
    public VerificationStatus getStatus(Long sessionId) {
        VerificationSession session = repository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_SESSION_NOT_FOUND, "Session ID: " + sessionId));

        // Optional: auto-expire check
        if (session.getStatus() == VerificationStatus.PENDING &&
            LocalDateTime.now().isAfter(session.getExpiresAt())) {

            session.setStatus(VerificationStatus.EXPIRED);
            repository.save(session);
        }

        return session.getStatus();
    }

    /**
     * Generiše nasumični 6-cifreni numerički kod za verifikaciju.
     *
     * @return generisani kod kao string
     */
    private String generateCode() {
        int code = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return String.valueOf(code);
    }
}
