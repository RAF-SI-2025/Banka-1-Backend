package com.banka1.transaction_service.controller;

import com.banka1.transaction_service.advice.GlobalExceptionHandler;
import com.banka1.transaction_service.dto.response.TransactionResponseDto;
import com.banka1.transaction_service.security.SecurityBeans;
import com.banka1.transaction_service.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
@Import({GlobalExceptionHandler.class, SecurityBeans.class})
@TestPropertySource(properties = {"jwt.secret=test-secret-key-for-unit-testing-12345678901234567890"})
class EmployeeControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void findAllTransactionsForEmployeeReturnsOkForAdmin() throws Exception {
        when(transactionService.findAllTransactionsForEmployee(anyString(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(new TransactionResponseDto())));

        mockMvc.perform(get("/employee/accounts/1110001000000000011")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void findAllTransactionsForEmployeeReturnsOkForSupervisor() throws Exception {
        when(transactionService.findAllTransactionsForEmployee(anyString(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(new TransactionResponseDto())));

        mockMvc.perform(get("/employee/accounts/1110001000000000011")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void findAllTransactionsForEmployeeReturnsOkForAgent() throws Exception {
        when(transactionService.findAllTransactionsForEmployee(anyString(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(new TransactionResponseDto())));

        mockMvc.perform(get("/employee/accounts/1110001000000000011")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AGENT"))))
                .andExpect(status().isOk());
    }

    @Test
    void findAllTransactionsForEmployeeReturnsOkForBasic() throws Exception {
        when(transactionService.findAllTransactionsForEmployee(anyString(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(new TransactionResponseDto())));

        mockMvc.perform(get("/employee/accounts/1110001000000000011")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BASIC"))))
                .andExpect(status().isOk());
    }

    @Test
    void findAllTransactionsForEmployeeReturnsForbiddenForClientBasic() throws Exception {
        mockMvc.perform(get("/employee/accounts/1110001000000000011")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT_BASIC"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void findAllTransactionsForEmployeeReturnsUnauthorizedWhenNoToken() throws Exception {
        mockMvc.perform(get("/employee/accounts/1110001000000000011"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findAllTransactionsForEmployeeReturnsPagedResult() throws Exception {
        when(transactionService.findAllTransactionsForEmployee("1110001000000000011", 0, 5))
                .thenReturn(new PageImpl<>(List.of(new TransactionResponseDto())));

        mockMvc.perform(get("/employee/accounts/1110001000000000011")
                        .param("page", "0")
                        .param("size", "5")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
