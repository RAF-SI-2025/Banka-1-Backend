package com.banka1.account_service.controller;

import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.service.CurrencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyControllerUnitTest {

    @Mock
    private CurrencyService currencyService;

    @Test
    void findAllReturnsOkAndDelegates() {
        CurrencyController controller = new CurrencyController(currencyService);
        List<Currency> expected = List.of(new Currency("Euro", CurrencyCode.EUR, "EUR", Set.of("EU"), "desc", Status.ACTIVE));
        when(currencyService.findAll()).thenReturn(expected);

        ResponseEntity<List<Currency>> response = controller.findAll(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(currencyService).findAll();
    }

    @Test
    void findAllPageReturnsOkAndDelegatesPagination() {
        CurrencyController controller = new CurrencyController(currencyService);
        Page<Currency> expected = new PageImpl<>(List.of(new Currency("Euro", CurrencyCode.EUR, "EUR", Set.of("EU"), "desc", Status.ACTIVE)));
        when(currencyService.findAllPage(2, 5)).thenReturn(expected);

        ResponseEntity<Page<Currency>> response = controller.findAllPage(null, 2, 5);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(currencyService).findAllPage(2, 5);
    }

    @Test
    void findAllByCodeNormalizesToUpperCase() {
        CurrencyController controller = new CurrencyController(currencyService);
        Currency expected = new Currency("Euro", CurrencyCode.EUR, "EUR", Set.of("EU"), "desc", Status.ACTIVE);
        when(currencyService.findByCode(CurrencyCode.EUR)).thenReturn(expected);

        ResponseEntity<Currency> response = controller.findAllByCode(null, "eur");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(currencyService).findByCode(CurrencyCode.EUR);
    }

    @Test
    void findByCodePathVariableNormalizesToUpperCase() {
        CurrencyController controller = new CurrencyController(currencyService);
        Currency expected = new Currency("Dinar", CurrencyCode.RSD, "RSD", Set.of("RS"), "desc", Status.ACTIVE);
        when(currencyService.findByCode(CurrencyCode.RSD)).thenReturn(expected);

        ResponseEntity<Currency> response = controller.findByCode(null, "rsd");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(currencyService).findByCode(CurrencyCode.RSD);
    }
}

