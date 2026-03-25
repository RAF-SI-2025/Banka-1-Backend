package com.banka1.account_service.controller;

import com.banka1.account_service.dto.request.EditAccountLimitDto;
import com.banka1.account_service.dto.request.EditAccountNameDto;
import com.banka1.account_service.dto.response.AccountResponseDto;
import com.banka1.account_service.dto.response.CardResponseDto;
import com.banka1.account_service.service.ClientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientControllerUnitTest {

    @Mock
    private ClientService clientService;

    @Test
    void findMyAccountsReturnsOkAndDelegates() {
        ClientController controller = new ClientController(clientService);
        Page<AccountResponseDto> page = new PageImpl<>(List.of(new AccountResponseDto()));
        when(clientService.findMyAccounts(null, 0, 10)).thenReturn(page);

        ResponseEntity<Page<AccountResponseDto>> response = controller.findMyAccounts(null, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(page);
        verify(clientService).findMyAccounts(null, 0, 10);
    }

    @Test
    void editAccountNameReturnsOkAndDelegates() {
        ClientController controller = new ClientController(clientService);
        EditAccountNameDto dto = new EditAccountNameDto("Novi naziv");
        when(clientService.editAccountName(null, "111000100000000011", dto)).thenReturn("ok");

        ResponseEntity<String> response = controller.editAccountName(null, "111000100000000011", dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("ok");
        verify(clientService).editAccountName(null, "111000100000000011", dto);
    }

    @Test
    void editAccountLimitReturnsOkAndDelegates() {
        ClientController controller = new ClientController(clientService);
        EditAccountLimitDto dto = new EditAccountLimitDto(new BigDecimal("100"), new BigDecimal("1000"), "1234", 1L);
        when(clientService.editAccountLimit(null, "111000100000000011", dto)).thenReturn("ok");

        ResponseEntity<String> response = controller.editAccountLimit(null, "111000100000000011", dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("ok");
        verify(clientService).editAccountLimit(null, "111000100000000011", dto);
    }

    @Test
    void findAllCardsReturnsOkAndDelegates() {
        ClientController controller = new ClientController(clientService);
        Page<CardResponseDto> page = new PageImpl<>(List.of(new CardResponseDto()));
        when(clientService.findAllCards(null, 1L, 0, 10)).thenReturn(page);

        ResponseEntity<Page<CardResponseDto>> response = controller.findAllCards(null, 1L, 0, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(page);
        verify(clientService).findAllCards(null, 1L, 0, 10);
    }
}


