package com.banka1.transfer.mapper;


import com.banka1.transfer.domain.Transfer;
import com.banka1.transfer.dto.requests.TransferRequestDto;
import com.banka1.transfer.dto.responses.TransferResponseDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TransferMapperTest {

    private final TransferMapper transferMapper = new TransferMapper();

    @Test
    void toEntity_ShouldMapAllFields() {
        TransferRequestDto request = new TransferRequestDto();
        request.setFromAccountNumber("111");
        request.setToAccountNumber("222");
        request.setAmount(new BigDecimal("100.00"));

        // PROMENA: Long ID
        request.setVerificationSessionId(123L);

        Transfer entity = transferMapper.toEntity(
                request, "TRF-001", 10L,
                new BigDecimal("95.00"), new BigDecimal("1.1"), new BigDecimal("5.00")
        );

        assertEquals("TRF-001", entity.getOrderNumber());
        assertEquals(10L, entity.getClientId());

        // PROMENA: Mapper pretvara Long u String za bazu ("123")
        assertEquals("123", entity.getVerificationSessionId());

        assertEquals(new BigDecimal("100.00"), entity.getInitialAmount());
        assertNotNull(entity.getTimestamp());
    }

    @Test
    void toDto_ShouldMapAllFields() {
        Transfer transfer = Transfer.builder()
                .orderNumber("TRF-123")
                .fromAccountNumber("111")
                .toAccountNumber("222")
                .initialAmount(BigDecimal.TEN)
                .finalAmount(BigDecimal.ONE)
                .timestamp(Instant.now())
                .build();

        TransferResponseDto dto = transferMapper.toDto(transfer);

        assertEquals("TRF-123", dto.getOrderNumber());
        assertEquals("111", dto.getFromAccountNumber());
    }
}