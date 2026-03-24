package com.banka1.verificationService.service;

import com.banka1.verificationService.dto.event.VerificationGeneratedEvent;
import com.banka1.verificationService.dto.request.GenerateRequest;
import com.banka1.verificationService.dto.request.ValidateRequest;
import com.banka1.verificationService.dto.response.GenerateResponse;
import com.banka1.verificationService.dto.response.ValidateResponse;
import com.banka1.verificationService.exception.BusinessException;
import com.banka1.verificationService.exception.ErrorCode;
import com.banka1.verificationService.model.entity.VerificationSession;
import com.banka1.verificationService.model.enums.OperationType;
import com.banka1.verificationService.model.enums.VerificationStatus;
import com.banka1.verificationService.repository.VerificationSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock
    private VerificationSessionRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private VerificationService verificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(verificationService, "exchange", "test-exchange");
        ReflectionTestUtils.setField(verificationService, "routingKey", "verification.generated");
    }

    @Test
    void generateCreatesPendingSessionWithHashedCodeAndPublishesEvent() {
        GenerateRequest request = new GenerateRequest();
        request.setClientId(55L);
        request.setOperationType(OperationType.PAYMENT);
        request.setRelatedEntityId("payment-123");

        when(passwordEncoder.encode(any())).thenAnswer(invocation -> "hashed-" + invocation.getArgument(0, String.class));
        when(repository.save(any(VerificationSession.class))).thenAnswer(invocation -> {
            VerificationSession session = invocation.getArgument(0);
            session.setId(99L);
            return session;
        });

        GenerateResponse response = verificationService.generate(request);

        ArgumentCaptor<VerificationSession> sessionCaptor = ArgumentCaptor.forClass(VerificationSession.class);
        verify(repository).save(sessionCaptor.capture());
        VerificationSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.getClientId()).isEqualTo(55L);
        assertThat(savedSession.getOperationType()).isEqualTo(OperationType.PAYMENT);
        assertThat(savedSession.getRelatedEntityId()).isEqualTo("payment-123");
        assertThat(savedSession.getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(savedSession.getAttemptCount()).isZero();
        assertThat(savedSession.getCode()).startsWith("hashed-");
        assertThat(savedSession.getCode()).doesNotMatch("^\\d{6}$");
        assertThat(Duration.between(savedSession.getCreatedAt(), savedSession.getExpiresAt())).isEqualTo(Duration.ofMinutes(5));

        ArgumentCaptor<VerificationGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(VerificationGeneratedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("test-exchange"), eq("verification.generated"), eventCaptor.capture());
        VerificationGeneratedEvent event = eventCaptor.getValue();
        assertThat(event.getClientId()).isEqualTo(55L);
        assertThat(event.getOperationType()).isEqualTo(OperationType.PAYMENT);
        assertThat(event.getCode()).matches("^\\d{6}$");
        assertThat(savedSession.getCode()).isNotEqualTo(event.getCode());
        assertThat(response.getSessionId()).isEqualTo(99L);
    }

    @Test
    void validateMarksSessionVerifiedWhenCodeMatches() {
        VerificationSession session = pendingSession();
        session.setId(10L);
        when(repository.findById(10L)).thenReturn(Optional.of(session));
        when(passwordEncoder.matches("123456", session.getCode())).thenReturn(true);

        ValidateRequest request = new ValidateRequest();
        request.setSessionId(10L);
        request.setCode("123456");

        ValidateResponse response = verificationService.validate(request);

        assertThat(response.isValid()).isTrue();
        assertThat(response.getStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(response.getRemainingAttempts()).isZero();
        assertThat(session.getStatus()).isEqualTo(VerificationStatus.VERIFIED);
        verify(repository).save(session);
    }

    @Test
    void validateIncrementsAttemptsForWrongCode() {
        VerificationSession session = pendingSession();
        session.setId(11L);
        when(repository.findById(11L)).thenReturn(Optional.of(session));
        when(passwordEncoder.matches("000000", session.getCode())).thenReturn(false);

        ValidateRequest request = new ValidateRequest();
        request.setSessionId(11L);
        request.setCode("000000");

        ValidateResponse response = verificationService.validate(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(response.getRemainingAttempts()).isEqualTo(2);
        assertThat(session.getAttemptCount()).isEqualTo(1);
        verify(repository).save(session);
    }

    @Test
    void validateCancelsSessionAfterThirdFailedAttempt() {
        VerificationSession session = pendingSession();
        session.setId(12L);
        session.setAttemptCount(2);
        when(repository.findById(12L)).thenReturn(Optional.of(session));
        when(passwordEncoder.matches("000000", session.getCode())).thenReturn(false);

        ValidateRequest request = new ValidateRequest();
        request.setSessionId(12L);
        request.setCode("000000");

        ValidateResponse response = verificationService.validate(request);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getStatus()).isEqualTo(VerificationStatus.CANCELLED);
        assertThat(response.getRemainingAttempts()).isZero();
        assertThat(session.getAttemptCount()).isEqualTo(3);
        assertThat(session.getStatus()).isEqualTo(VerificationStatus.CANCELLED);
        verify(repository).save(session);
    }

    @Test
    void validateRejectsExpiredSessionAndMarksItExpired() {
        VerificationSession session = pendingSession();
        session.setId(13L);
        session.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findById(13L)).thenReturn(Optional.of(session));

        ValidateRequest request = new ValidateRequest();
        request.setSessionId(13L);
        request.setCode("123456");

        assertThatThrownBy(() -> verificationService.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Verifikacioni kod je istekao: Session ID: 13")
                .extracting("errorCode").isEqualTo(ErrorCode.VERIFICATION_CODE_EXPIRED);

        assertThat(session.getStatus()).isEqualTo(VerificationStatus.EXPIRED);
        verify(repository).save(session);
    }

    @Test
    void validateRejectsCancelledSession() {
        VerificationSession session = pendingSession();
        session.setId(14L);
        session.setStatus(VerificationStatus.CANCELLED);
        when(repository.findById(14L)).thenReturn(Optional.of(session));

        ValidateRequest request = new ValidateRequest();
        request.setSessionId(14L);
        request.setCode("123456");

        assertThatThrownBy(() -> verificationService.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Sesija verifikacije je otkazana: Session ID: 14")
                .extracting("errorCode").isEqualTo(ErrorCode.VERIFICATION_SESSION_CANCELLED);
    }

    @Test
    void getStatusAutoExpiresPendingSession() {
        VerificationSession session = pendingSession();
        session.setId(15L);
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(repository.findById(15L)).thenReturn(Optional.of(session));

        VerificationStatus status = verificationService.getStatus(15L);

        assertThat(status).isEqualTo(VerificationStatus.EXPIRED);
        verify(repository).save(session);
    }

    @Test
    void getStatusThrowsWhenSessionIsMissing() {
        when(repository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verificationService.getStatus(77L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Sesija verifikacije nije pronađena: Session ID: 77")
                .extracting("errorCode").isEqualTo(ErrorCode.VERIFICATION_SESSION_NOT_FOUND);
    }

    private VerificationSession pendingSession() {
        LocalDateTime now = LocalDateTime.now();
        return VerificationSession.builder()
                .id(1L)
                .clientId(44L)
                .code("hashed-code")
                .operationType(OperationType.TRANSFER)
                .relatedEntityId("transfer-1")
                .createdAt(now)
                .expiresAt(now.plusMinutes(5))
                .attemptCount(0)
                .status(VerificationStatus.PENDING)
                .build();
    }
}
