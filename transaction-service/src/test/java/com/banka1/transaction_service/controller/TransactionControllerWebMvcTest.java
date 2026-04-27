package com.banka1.transaction_service.controller;

import com.banka1.transaction_service.advice.GlobalExceptionHandler;
import com.banka1.transaction_service.domain.enums.TransactionStatus;
import com.banka1.transaction_service.dto.request.NewPaymentDto;
import com.banka1.transaction_service.dto.response.NewPaymentResponseDto;
import com.banka1.transaction_service.dto.response.TransactionResponseDto;
import com.banka1.transaction_service.security.SecurityBeans;
import com.banka1.transaction_service.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import({GlobalExceptionHandler.class, SecurityBeans.class})
@TestPropertySource(properties = {"jwt.secret=test-secret-key-for-unit-testing-12345678901234567890"})
class TransactionControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void newPaymentReturnsOkForClientBasic() throws Exception {
        NewPaymentDto dto = validPaymentDto();
        when(transactionService.newPayment(any(), any()))
                .thenReturn(new NewPaymentResponseDto("Uspesan payment", "COMPLETED"));

        mockMvc.perform(post("/payment")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_BASIC")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void newPaymentReturnsForbiddenForEmployee() throws Exception {
        NewPaymentDto dto = validPaymentDto();

        mockMvc.perform(post("/payment")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BASIC")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void newPaymentReturnsBadRequestForInvalidDto() throws Exception {
        NewPaymentDto dto = new NewPaymentDto();
        // All required fields are null - should fail validation

        mockMvc.perform(post("/payment")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_BASIC")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void newPaymentAlsoWorksOnPaymentsAlias() throws Exception {
        NewPaymentDto dto = validPaymentDto();
        when(transactionService.newPayment(any(), any()))
                .thenReturn(new NewPaymentResponseDto("Uspesan payment", "COMPLETED"));

        mockMvc.perform(post("/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_BASIC")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    void newPaymentReturnsForbiddenWhenNoTokenBecauseCsrf() throws Exception {
        NewPaymentDto dto = validPaymentDto();

        mockMvc.perform(post("/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void findAllTransactionsReturnsOkForClientBasic() throws Exception {
        when(transactionService.findAllTransactions(any(), eq("1110001000000000011"), eq(0), eq(10)))
                .thenReturn(new PageImpl<>(List.of(new TransactionResponseDto())));

        mockMvc.perform(get("/accounts/1110001000000000011")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_BASIC"))))
                .andExpect(status().isOk());
    }

    @Test
    void findAllTransactionsReturnsOkForEmployee() throws Exception {
        when(transactionService.findAllTransactions(any(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(new TransactionResponseDto())));

        mockMvc.perform(get("/accounts/1110001000000000011")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BASIC"))))
                .andExpect(status().isOk());
    }

    @Test
    void findAllTransactionsReturnsUnauthorizedWhenNoToken() throws Exception {
        mockMvc.perform(get("/accounts/1110001000000000011"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findPaymentsReturnsOkForClientBasic() throws Exception {
        when(transactionService.findPayments(any(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(new PageImpl<>(List.of(new TransactionResponseDto())));

        mockMvc.perform(get("/api/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_BASIC"))))
                .andExpect(status().isOk());
    }

    @Test
    void findPaymentsReturnsOkForEmployee() throws Exception {
        when(transactionService.findPayments(any(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(new PageImpl<>(List.of(new TransactionResponseDto())));

        mockMvc.perform(get("/api/payments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BASIC"))))
                .andExpect(status().isOk());
    }

    @Test
    void findPaymentsWithStatusFilterReturnsOk() throws Exception {
        when(transactionService.findPayments(any(), isNull(), eq(TransactionStatus.COMPLETED),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/payments")
                        .param("status", "COMPLETED")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_BASIC"))))
                .andExpect(status().isOk());
    }

    @Test
    void findPaymentsWithInvalidStatusReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/payments")
                        .param("status", "INVALID_STATUS")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_BASIC"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findPaymentsReturnsUnauthorizedWhenNoToken() throws Exception {
        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isUnauthorized());
    }

    private NewPaymentDto validPaymentDto() {
        NewPaymentDto dto = new NewPaymentDto();
        dto.setFromAccountNumber("1110001000000000011");
        dto.setToAccountNumber("1110001000000000012");
        dto.setAmount(new BigDecimal("100.00"));
        dto.setRecipientName("Pera Peric");
        dto.setPaymentCode("289");
        dto.setPaymentPurpose("Stanarino");
        dto.setVerificationSessionId(1L);
        return dto;
    }
}
