package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.repository.CurrencyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceImplementationTest {

    @Mock private CurrencyRepository currencyRepository;

    @InjectMocks
    private CurrencyServiceImplementation service;

    private static final Currency RSD = new Currency("Dinar", CurrencyCode.RSD, "din", Set.of("RS"), "desc", Status.ACTIVE);
    private static final Currency EUR = new Currency("Euro", CurrencyCode.EUR, "€", Set.of("EU"), "desc", Status.ACTIVE);

    @Test
    void findAllReturnsActiveCurrencies() {
        when(currencyRepository.findByStatus(Status.ACTIVE)).thenReturn(List.of(RSD, EUR));

        List<Currency> result = service.findAll();

        assertThat(result).containsExactly(RSD, EUR);
        verify(currencyRepository).findByStatus(Status.ACTIVE);
    }

    @Test
    void findAllReturnsEmptyListWhenNoCurrencies() {
        when(currencyRepository.findByStatus(Status.ACTIVE)).thenReturn(List.of());

        List<Currency> result = service.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void findAllPageReturnsPaginatedResults() {
        PageImpl<Currency> page = new PageImpl<>(List.of(RSD, EUR), PageRequest.of(0, 10), 2);
        when(currencyRepository.findByStatus(eq(Status.ACTIVE), any(Pageable.class))).thenReturn(page);

        Page<Currency> result = service.findAllPage(0, 10);

        assertThat(result.getContent()).containsExactly(RSD, EUR);
        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(currencyRepository).findByStatus(eq(Status.ACTIVE), any(Pageable.class));
    }

    @Test
    void findAllPagePassesCorrectPageRequest() {
        PageImpl<Currency> page = new PageImpl<>(List.of(EUR), PageRequest.of(1, 5), 6);
        when(currencyRepository.findByStatus(eq(Status.ACTIVE), eq(PageRequest.of(1, 5)))).thenReturn(page);

        Page<Currency> result = service.findAllPage(1, 5);

        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(5);
    }

    @Test
    void findByCodeReturnsCurrencyWhenFound() {
        when(currencyRepository.findByOznaka(CurrencyCode.EUR)).thenReturn(Optional.of(EUR));

        Currency result = service.findByCode(CurrencyCode.EUR);

        assertThat(result).isSameAs(EUR);
    }

    @Test
    void findByCodeThrowsWhenNotFound() {
        when(currencyRepository.findByOznaka(CurrencyCode.CHF)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCode(CurrencyCode.CHF))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Currency nije pronadjen");
    }

    @Test
    void findByCodeRsdReturnsRsd() {
        when(currencyRepository.findByOznaka(CurrencyCode.RSD)).thenReturn(Optional.of(RSD));

        Currency result = service.findByCode(CurrencyCode.RSD);

        assertThat(result.getOznaka()).isEqualTo(CurrencyCode.RSD);
        assertThat(result.getNaziv()).isEqualTo("Dinar");
    }
}
