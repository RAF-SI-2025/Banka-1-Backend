package com.banka1.account_service.controller;

import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.dto.request.PaymentDto;
import com.banka1.account_service.dto.response.InfoResponseDto;
import com.banka1.account_service.dto.response.InternalAccountDetailsDto;
import com.banka1.account_service.dto.response.UpdatedBalanceResponseDto;
import com.banka1.account_service.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountControllerUnitTest {

    @Mock
    private AccountService accountService;

    @Test
    void transactionReturnsOkAndDelegates() {
        AccountController controller = new AccountController(accountService);
        PaymentDto dto = paymentDto();
        UpdatedBalanceResponseDto expected = new UpdatedBalanceResponseDto(new BigDecimal("900"), new BigDecimal("100"));
        when(accountService.transaction(dto)).thenReturn(expected);

        ResponseEntity<UpdatedBalanceResponseDto> response = controller.transaction(null, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(accountService).transaction(dto);
    }

    @Test
    void transferReturnsOkAndDelegates() {
        AccountController controller = new AccountController(accountService);
        PaymentDto dto = paymentDto();
        UpdatedBalanceResponseDto expected = new UpdatedBalanceResponseDto(new BigDecimal("800"), new BigDecimal("200"));
        when(accountService.transfer(dto)).thenReturn(expected);

        ResponseEntity<UpdatedBalanceResponseDto> response = controller.transfer(null, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(accountService).transfer(dto);
    }

    @Test
    void infoReturnsOkAndDelegates() {
        AccountController controller = new AccountController(accountService);
        InfoResponseDto expected = new InfoResponseDto(CurrencyCode.RSD, CurrencyCode.EUR, 1L, 2L, null, null);
        when(accountService.info(null, "111000100000000011", "111000100000000012")).thenReturn(expected);

        ResponseEntity<InfoResponseDto> response = controller.info(null, "111000100000000011", "111000100000000012");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(accountService).info(null, "111000100000000011", "111000100000000012");
    }

    @Test
    void getAccountDetailsByIdReturnsOkAndDelegates() {
        AccountController controller = new AccountController(accountService);
        InternalAccountDetailsDto expected = new InternalAccountDetailsDto("111000100000000011", 1L, "RSD", new BigDecimal("250.00"), "ACTIVE", "PERSONAL", null, null);
        when(accountService.getAccountDetails(42L)).thenReturn(expected);

        ResponseEntity<InternalAccountDetailsDto> response = controller.getAccountDetailsById(null, 42L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(accountService).getAccountDetails(42L);
    }

    @Test
    void getBankAccountDetailsReturnsOkAndDelegates() {
        AccountController controller = new AccountController(accountService);
        InternalAccountDetailsDto expected = new InternalAccountDetailsDto("111000100000000099", -1L, "RSD", new BigDecimal("1000.00"), "ACTIVE", "PERSONAL", null, null);
        when(accountService.getBankAccountDetails(CurrencyCode.RSD)).thenReturn(expected);

        ResponseEntity<InternalAccountDetailsDto> response = controller.getBankAccountDetails(null, CurrencyCode.RSD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(accountService).getBankAccountDetails(CurrencyCode.RSD);
    }

    @Test
    void getStateAccountDetailsReturnsOkAndDelegates() {
        AccountController controller = new AccountController(accountService);
        InternalAccountDetailsDto expected = new InternalAccountDetailsDto("1110002000000000011", -2L, "RSD", BigDecimal.ZERO, "ACTIVE", "PERSONAL", null, null);
        when(accountService.getStateAccountDetails(CurrencyCode.RSD)).thenReturn(expected);

        ResponseEntity<InternalAccountDetailsDto> response = controller.getStateAccountDetails(null, CurrencyCode.RSD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(accountService).getStateAccountDetails(CurrencyCode.RSD);
    }

    @Test
    void getStateAccountDetailsRejectsNonRsdCurrency() {
        AccountController controller = new AccountController(accountService);

        assertThatThrownBy(() -> controller.getStateAccountDetails(null, CurrencyCode.EUR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RSD");

        verify(accountService, never()).getStateAccountDetails(any());
    }

    private PaymentDto paymentDto() {
        return new PaymentDto(
                "111000100000000011",
                "111000100000000012",
                new BigDecimal("100"),
                new BigDecimal("100"),
                BigDecimal.ZERO,
                1L
        );
    }
}
