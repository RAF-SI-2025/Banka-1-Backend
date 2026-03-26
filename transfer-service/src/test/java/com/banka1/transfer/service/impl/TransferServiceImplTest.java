package com.banka1.transfer.service.impl;

import com.banka1.transfer.client.*;
import com.banka1.transfer.domain.Transfer;
import com.banka1.transfer.dto.client.*;
import com.banka1.transfer.dto.requests.TransferRequestDto;
import com.banka1.transfer.dto.responses.TransferResponseDto;
import com.banka1.transfer.exception.BusinessException;
import com.banka1.transfer.mapper.TransferMapper;
import com.banka1.transfer.rabbitmq.EmailDto;
import com.banka1.transfer.rabbitmq.RabbitClient;
import com.banka1.transfer.repository.TransferRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock private TransferRepository transferRepository;
    @Mock private TransferMapper transferMapper;
    @Mock private AccountClient accountClient;
    @Mock private ExchangeClient exchangeClient;
    @Mock private VerificationClient verificationClient;
    @Mock private RabbitClient rabbitClient;
    @Mock private ClientClient clientClient;
    @Mock private Jwt jwt;

    @InjectMocks
    private TransferServiceImpl transferService;

    @BeforeEach
    void setUp() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    private void mockJwt(String id, String role) {
        when(jwt.getClaimAsString("id")).thenReturn(id);
        when(jwt.getClaimAsStringList("roles")).thenReturn(List.of(role));
    }

    @Test
    void executeTransfer_Success_SameCurrency() {
        // Arrange
        TransferRequestDto request = new TransferRequestDto();
        request.setFromAccountNumber("111");
        request.setToAccountNumber("222");
        request.setAmount(new BigDecimal("100"));
        request.setVerificationSessionId(1L);

        mockJwt("1", "ROLE_CLIENT_BASIC");
        AccountDto fromAcc = new AccountDto("111", 1L, "RSD", new BigDecimal("1000"), "ACTIVE", "CURRENT");
        AccountDto toAcc = new AccountDto("222", 1L, "RSD", new BigDecimal("500"), "ACTIVE", "CURRENT");
        ClientInfoResponseDto clientInfo = new ClientInfoResponseDto(1L, "Pera", "Peric", "pera@gmail.com");

        when(transferRepository.existsByVerificationSessionId("1")).thenReturn(false);
        // NOVO: Provera statusa sesije
        when(verificationClient.getVerificationStatus(1L)).thenReturn(new VerificationResponseDto(1L, "VERIFIED"));

        when(accountClient.getAccountDetails("111")).thenReturn(fromAcc);
        when(accountClient.getAccountDetails("222")).thenReturn(toAcc);
        when(transferMapper.toEntity(any(), any(), any(), any(), any(), any())).thenReturn(new Transfer());
        when(transferRepository.save(any(Transfer.class))).thenReturn(new Transfer());
        when(clientClient.getClientDetails(1L)).thenReturn(clientInfo);
        when(transferMapper.toDto(any())).thenReturn(new TransferResponseDto());

        // Act
        TransferResponseDto result = transferService.executeTransfer(jwt, request);

        // Assert
        assertNotNull(result);
        verify(accountClient, times(1)).executeTransfer(any(PaymentDto.class));

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        verify(rabbitClient, times(1)).sendEmailNotification(any(EmailDto.class));
    }

    @Test
    void executeTransfer_Success_DifferentCurrency() {
        // Arrange
        TransferRequestDto request = new TransferRequestDto();
        request.setFromAccountNumber("111");
        request.setToAccountNumber("222");
        request.setAmount(new BigDecimal("100"));
        request.setVerificationSessionId(1L);

        mockJwt("1", "ROLE_CLIENT_BASIC");
        AccountDto fromAcc = new AccountDto("111", 1L, "RSD", new BigDecimal("1000"), "ACTIVE", "CURRENT");
        AccountDto toAcc = new AccountDto("222", 1L, "EUR", new BigDecimal("500"), "ACTIVE", "CURRENT");
        ExchangeResponseDto exchangeResp = new ExchangeResponseDto("RSD", "EUR", new BigDecimal("100"), new BigDecimal("0.85"), new BigDecimal("117.2"), BigDecimal.ZERO);

        when(transferRepository.existsByVerificationSessionId("1")).thenReturn(false);
        // NOVO: Provera statusa sesije
        when(verificationClient.getVerificationStatus(1L)).thenReturn(new VerificationResponseDto(1L, "VERIFIED"));

        when(accountClient.getAccountDetails("111")).thenReturn(fromAcc);
        when(accountClient.getAccountDetails("222")).thenReturn(toAcc);
        when(exchangeClient.calculateExchange("RSD", "EUR", new BigDecimal("100"))).thenReturn(exchangeResp);
        when(transferMapper.toEntity(any(), any(), any(), any(), any(), any())).thenReturn(new Transfer());
        when(transferRepository.save(any(Transfer.class))).thenReturn(new Transfer());
        when(clientClient.getClientDetails(1L)).thenReturn(new ClientInfoResponseDto(1L, "Pera", "P", "p@p.com"));

        // Act
        transferService.executeTransfer(jwt, request);

        // Assert
        verify(exchangeClient, times(1)).calculateExchange("RSD", "EUR", new BigDecimal("100"));
    }

    @Test
    void executeTransfer_Fail_Idempotency() {
        TransferRequestDto request = new TransferRequestDto();
        request.setVerificationSessionId(99L);

        when(transferRepository.existsByVerificationSessionId("99")).thenReturn(true);

        assertThrows(BusinessException.class, () -> transferService.executeTransfer(jwt, request));
    }

    @Test
    void executeTransfer_Fail_SameAccount() {
        TransferRequestDto request = new TransferRequestDto();
        request.setFromAccountNumber("111");
        request.setToAccountNumber("111");
        request.setVerificationSessionId(1L);

        when(transferRepository.existsByVerificationSessionId("1")).thenReturn(false);

        assertThrows(BusinessException.class, () -> transferService.executeTransfer(jwt, request));
    }

    @Test
    void executeTransfer_Fail_Invalid2FA() {
        TransferRequestDto request = new TransferRequestDto();
        request.setFromAccountNumber("111");
        request.setToAccountNumber("222");
        request.setVerificationSessionId(1L);

        when(transferRepository.existsByVerificationSessionId("1")).thenReturn(false);
        // NOVO: Status nije VERIFIED (npr. PENDING ili EXPIRED)
        when(verificationClient.getVerificationStatus(1L)).thenReturn(new VerificationResponseDto(1L, "PENDING"));

        assertThrows(BusinessException.class, () -> transferService.executeTransfer(jwt, request));
    }

    @Test
    void executeTransfer_Fail_NotOwner() {
        TransferRequestDto request = new TransferRequestDto();
        request.setFromAccountNumber("111");
        request.setVerificationSessionId(1L);

        mockJwt("2", "ROLE_CLIENT_BASIC"); // Logovan User 2
        AccountDto fromAcc = new AccountDto("111", 1L, "RSD", BigDecimal.TEN, "ACTIVE", "C"); // Vlasnik je 1

        when(transferRepository.existsByVerificationSessionId("1")).thenReturn(false);
        // Prolazi verifikaciju ali pada na owner checku
        when(verificationClient.getVerificationStatus(1L)).thenReturn(new VerificationResponseDto(1L, "VERIFIED"));

        when(accountClient.getAccountDetails(any())).thenReturn(fromAcc);

        assertThrows(BusinessException.class, () -> transferService.executeTransfer(jwt, request));
    }

    @Test
    void getClientTransfers_ReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Transfer> transferPage = new PageImpl<>(List.of(new Transfer()));

        when(transferRepository.findByClientId(1L, pageable)).thenReturn(transferPage);
        when(transferMapper.toDto(any())).thenReturn(new TransferResponseDto());

        Page<TransferResponseDto> result = transferService.getClientTransfers(1L, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getTransferDetails_Success() {
        Transfer transfer = new Transfer();
        transfer.setOrderNumber("TRF-123");
        transfer.setClientId(1L);

        mockJwt("1", "ROLE_CLIENT_BASIC");
        when(transferRepository.findByOrderNumber("TRF-123")).thenReturn(Optional.of(transfer));
        when(transferMapper.toDto(transfer)).thenReturn(new TransferResponseDto());

        TransferResponseDto result = transferService.getTransferDetails(jwt, "TRF-123");

        assertNotNull(result);
    }

    @Test
    void getTransferDetails_Forbidden_NotOwner() {
        Transfer transfer = new Transfer();
        transfer.setClientId(1L); // Vlasnik 1

        mockJwt("2", "ROLE_CLIENT_BASIC"); // Logovan 2
        when(transferRepository.findByOrderNumber("ANY")).thenReturn(Optional.of(transfer));

        assertThrows(BusinessException.class, () -> transferService.getTransferDetails(jwt, "ANY"));
    }

    @Test
    void getTransfersByAccountNumber_ReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        AccountDto acc = new AccountDto("111", 1L, "RSD", BigDecimal.ZERO, "ACTIVE", "C");

        mockJwt("1", "ROLE_CLIENT_BASIC");
        when(accountClient.getAccountDetails("111")).thenReturn(acc);
        when(transferRepository.findByFromAccountNumberOrToAccountNumber("111", "111", pageable))
                .thenReturn(new PageImpl<>(List.of(new Transfer())));
        when(transferMapper.toDto(any())).thenReturn(new TransferResponseDto());

        Page<TransferResponseDto> result = transferService.getTransfersByAccountNumber(jwt, "111", pageable);

        assertNotNull(result);
    }

    @Test
    void getTransfersByAccountNumber_NotFound() {
        when(accountClient.getAccountDetails("999")).thenReturn(null);
        assertThrows(BusinessException.class, () -> transferService.getTransfersByAccountNumber(jwt, "999", PageRequest.of(0, 10)));
    }
}