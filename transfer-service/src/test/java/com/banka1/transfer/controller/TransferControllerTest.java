package com.banka1.transfer.controller;

import com.banka1.transfer.dto.requests.TransferRequestDto;
import com.banka1.transfer.dto.responses.TransferResponseDto;
import com.banka1.transfer.service.TransferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void executeTransfer_Success() throws Exception {
        TransferRequestDto request = new TransferRequestDto();
        request.setFromAccountNumber("123");
        request.setToAccountNumber("456");
        request.setAmount(new BigDecimal("100.00"));
        request.setVerificationSessionId(1L);

        TransferResponseDto response = new TransferResponseDto();
        response.setOrderNumber("TRF-001");

        when(transferService.executeTransfer(any(Jwt.class), any())).thenReturn(response);

        mockMvc.perform(post("/")
                        .with(jwt()) // Simulira JWT bez posebnih claimova za POST jer ih kontroler ne koristi za logiku
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("TRF-001"));
    }

    @Test
    void getClientTransfers_Success_OwnHistory() throws Exception {
        Long clientId = 10L;
        // Simuliramo JWT gde je ID klijenta 10 (kao string, jer Claims tako vraćaju)
        var jwtPostProcessor = jwt().jwt(builder -> builder.claim("id", "10").claim("roles", List.of("ROLE_USER")));

        when(transferService.getClientTransfers(eq(clientId), any()))
                .thenReturn(new PageImpl<>(List.of(new TransferResponseDto())));

        mockMvc.perform(get("/")
                        .param("clientId", clientId.toString())
                        .with(jwtPostProcessor))
                .andExpect(status().isOk());
    }

    @Test
    void getClientTransfers_Forbidden_MismatchId() throws Exception {
        Long clientId = 10L;
        // Korisnik 11 pokušava da gleda istoriju korisnika 10
        var jwtPostProcessor = jwt().jwt(builder -> builder.claim("id", "11").claim("roles", List.of("ROLE_USER")));

        mockMvc.perform(get("/")
                        .param("clientId", clientId.toString())
                        .with(jwtPostProcessor))
                .andExpect(status().isForbidden());
    }

    @Test
    void getClientTransfers_Success_EmployeeAccess() throws Exception {
        Long clientId = 10L;
        // Korisnik 11 (Admin) gleda istoriju korisnika 10
        var jwtPostProcessor = jwt().jwt(builder -> builder.claim("id", "11").claim("roles", List.of("ROLE_ADMIN")));

        when(transferService.getClientTransfers(eq(clientId), any()))
                .thenReturn(new PageImpl<>(List.of(new TransferResponseDto())));

        mockMvc.perform(get("/")
                        .param("clientId", clientId.toString())
                        .with(jwtPostProcessor))
                .andExpect(status().isOk());
    }

    @Test
    void getTransferDetails_Success() throws Exception {
        String orderNo = "TRF-123";
        TransferResponseDto response = new TransferResponseDto();
        response.setOrderNumber(orderNo);

        when(transferService.getTransferDetails(any(Jwt.class), eq(orderNo))).thenReturn(response);

        mockMvc.perform(get("/{orderNumber}", orderNo).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNo));
    }

    @Test
    void getAccountTransfers_Success() throws Exception {
        String accNo = "111222333";
        when(transferService.getTransfersByAccountNumber(any(Jwt.class), eq(accNo), any()))
                .thenReturn(new PageImpl<>(List.of(new TransferResponseDto())));

        mockMvc.perform(get("/accounts/{accountNumber}", accNo)
                        .with(jwt().jwt(builder -> builder.claim("id", "10").claim("roles", List.of("ROLE_USER")))))
                .andExpect(status().isOk());
    }

    @Test
    void executeTransfer_ValidationFailed_NegativeAmount() throws Exception {
        TransferRequestDto request = new TransferRequestDto();
        request.setAmount(new BigDecimal("-10.00")); // Nevalidno!

        mockMvc.perform(post("/")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()); // Očekujemo 400 jer @Valid reaguje
    }
}