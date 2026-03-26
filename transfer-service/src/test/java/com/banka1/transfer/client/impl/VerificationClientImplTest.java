package com.banka1.transfer.client.impl;

import com.banka1.transfer.dto.client.VerificationResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificationClientImplTest {

    private VerificationClientImpl verificationClient;
    @Mock private RestClient restClient;
    @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        verificationClient = new VerificationClientImpl(restClient);
    }

    @Test
    void getVerificationStatus_Success() {
        VerificationResponseDto expected = new VerificationResponseDto(123L, "VERIFIED");

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), anyLong())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(VerificationResponseDto.class)).thenReturn(expected);

        VerificationResponseDto result = verificationClient.getVerificationStatus(123L);

        assertTrue(result.isVerified());
        verify(restClient).get();
    }
}