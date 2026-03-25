package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.CheckingAccount;
import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.FxAccount;
import com.banka1.account_service.domain.enums.AccountConcrete;
import com.banka1.account_service.domain.enums.AccountOwnershipType;
import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.dto.request.PaymentDto;
import com.banka1.account_service.dto.response.InfoResponseDto;
import com.banka1.account_service.dto.response.UpdatedBalanceResponseDto;
import com.banka1.account_service.repository.AccountRepository;
import com.banka1.account_service.service.TransactionalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplementationTest {

    @Mock private TransactionalService transactionalService;
    @Mock private AccountRepository accountRepository;

    @InjectMocks
    private AccountServiceImplementation service;

    private static final Currency RSD = new Currency("Dinar", CurrencyCode.RSD, "din", Set.of("RS"), "desc", Status.ACTIVE);
    private static final Currency EUR = new Currency("Euro", CurrencyCode.EUR, "€", Set.of("EU"), "desc", Status.ACTIVE);

    // ──────────────────── transaction ────────────────────

    @Test
    void transactionSucceedsWhenAccountsHaveDifferentOwners() {
        CheckingAccount from = checkingAccount("111000110000000011", 1L, RSD);
        CheckingAccount to = checkingAccount("111000110000000022", 2L, RSD);
        CheckingAccount bank = checkingAccount("111000110000000099", -1L, RSD);

        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));
        when(accountRepository.findByBrojRacuna("111000110000000022")).thenReturn(Optional.of(to));
        when(accountRepository.findByVlasnikAndCurrency(eq(-1L), eq(RSD))).thenReturn(Optional.of(bank));

        PaymentDto dto = payment("111000110000000011", "111000110000000022", "100", "100", "0", 1L);
        UpdatedBalanceResponseDto expected = new UpdatedBalanceResponseDto(BigDecimal.TEN, BigDecimal.ONE);
        // Both accounts use RSD so the same bank account is used for both bankSender and bankTarget
        when(transactionalService.transfer(any(), any(), any(), any(), any())).thenReturn(expected);

        UpdatedBalanceResponseDto result = service.transaction(dto);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void transactionThrowsWhenSameOwner() {
        CheckingAccount from = checkingAccount("111000110000000011", 1L, RSD);
        CheckingAccount to = checkingAccount("111000110000000022", 1L, RSD);
        CheckingAccount bank = checkingAccount("111000110000000099", -1L, RSD);

        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));
        when(accountRepository.findByBrojRacuna("111000110000000022")).thenReturn(Optional.of(to));
        when(accountRepository.findByVlasnikAndCurrency(eq(-1L), eq(RSD))).thenReturn(Optional.of(bank));

        PaymentDto dto = payment("111000110000000011", "111000110000000022", "100", "100", "0", 1L);

        assertThatThrownBy(() -> service.transaction(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("istog vlasnike");
    }

    @Test
    void transactionThrowsWhenFromAccountNotFound() {
        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.empty());

        PaymentDto dto = payment("111000110000000011", "111000110000000022", "100", "100", "0", 1L);

        assertThatThrownBy(() -> service.transaction(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("111000110000000011");
    }

    @Test
    void transactionThrowsWhenAccountInactive() {
        CheckingAccount from = checkingAccount("111000110000000011", 1L, RSD);
        from.setStatus(Status.INACTIVE);

        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));

        PaymentDto dto = payment("111000110000000011", "111000110000000022", "100", "100", "0", 1L);

        assertThatThrownBy(() -> service.transaction(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neaktivan");
    }

    @Test
    void transactionThrowsWhenAccountExpired() {
        CheckingAccount from = checkingAccount("111000110000000011", 1L, RSD);
        from.setDatumIsteka(LocalDate.now().minusDays(1));

        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));

        PaymentDto dto = payment("111000110000000011", "111000110000000022", "100", "100", "0", 1L);

        assertThatThrownBy(() -> service.transaction(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("istekao");
    }

    @Test
    void transactionThrowsWhenNotAccountOwner() {
        CheckingAccount from = checkingAccount("111000110000000011", 1L, RSD);
        CheckingAccount to = checkingAccount("111000110000000022", 2L, RSD);
        CheckingAccount bank = checkingAccount("111000110000000099", -1L, RSD);

        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));
        when(accountRepository.findByBrojRacuna("111000110000000022")).thenReturn(Optional.of(to));
        when(accountRepository.findByVlasnikAndCurrency(eq(-1L), eq(RSD))).thenReturn(Optional.of(bank));

        // clientId = 99 but account owner is 1
        PaymentDto dto = payment("111000110000000011", "111000110000000022", "100", "100", "0", 99L);

        assertThatThrownBy(() -> service.transaction(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nisi vlasnik racuna");
    }

    @Test
    void transactionThrowsWhenBankAccountNotFound() {
        CheckingAccount from = checkingAccount("111000110000000011", 1L, RSD);
        CheckingAccount to = checkingAccount("111000110000000022", 2L, RSD);

        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));
        when(accountRepository.findByBrojRacuna("111000110000000022")).thenReturn(Optional.of(to));
        when(accountRepository.findByVlasnikAndCurrency(eq(-1L), eq(RSD))).thenReturn(Optional.empty());

        PaymentDto dto = payment("111000110000000011", "111000110000000022", "100", "100", "0", 1L);

        assertThatThrownBy(() -> service.transaction(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fali banka");
    }

    // ──────────────────── transfer ────────────────────

    @Test
    void transferSucceedsWhenSameOwner() {
        CheckingAccount from = checkingAccount("111000110000000011", 5L, RSD);
        CheckingAccount to = checkingAccount("111000110000000022", 5L, RSD);
        CheckingAccount bank = checkingAccount("111000110000000099", -1L, RSD);

        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));
        when(accountRepository.findByBrojRacuna("111000110000000022")).thenReturn(Optional.of(to));
        when(accountRepository.findByVlasnikAndCurrency(eq(-1L), eq(RSD))).thenReturn(Optional.of(bank));

        PaymentDto dto = payment("111000110000000011", "111000110000000022", "200", "200", "0", 5L);
        UpdatedBalanceResponseDto expected = new UpdatedBalanceResponseDto(BigDecimal.ZERO, BigDecimal.TEN);
        when(transactionalService.transfer(any(), any(), any(), any(), any())).thenReturn(expected);

        UpdatedBalanceResponseDto result = service.transfer(dto);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void transferThrowsWhenDifferentOwners() {
        CheckingAccount from = checkingAccount("111000110000000011", 1L, RSD);
        CheckingAccount to = checkingAccount("111000110000000022", 2L, RSD);
        CheckingAccount bank = checkingAccount("111000110000000099", -1L, RSD);

        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));
        when(accountRepository.findByBrojRacuna("111000110000000022")).thenReturn(Optional.of(to));
        when(accountRepository.findByVlasnikAndCurrency(eq(-1L), eq(RSD))).thenReturn(Optional.of(bank));

        PaymentDto dto = payment("111000110000000011", "111000110000000022", "200", "200", "0", 1L);

        assertThatThrownBy(() -> service.transfer(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("istog vlasnika");
    }

    @Test
    void transferThrowsWhenClientIdNull() {
        CheckingAccount from = checkingAccount("111000110000000011", 1L, RSD);
        CheckingAccount to = checkingAccount("111000110000000022", 1L, RSD);
        CheckingAccount bank = checkingAccount("111000110000000099", -1L, RSD);

        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));
        when(accountRepository.findByBrojRacuna("111000110000000022")).thenReturn(Optional.of(to));
        when(accountRepository.findByVlasnikAndCurrency(eq(-1L), eq(RSD))).thenReturn(Optional.of(bank));

        PaymentDto dto = payment("111000110000000011", "111000110000000022", "200", "200", "0", null);

        assertThatThrownBy(() -> service.transfer(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unesi id clienta");
    }

    // ──────────────────── info ────────────────────

    @Test
    void infoReturnsCorrectCurrenciesForSameCurrency() {
        CheckingAccount from = checkingAccount("111000110000000011", 1L, RSD);
        CheckingAccount to = checkingAccount("111000110000000022", 2L, RSD);

        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));
        when(accountRepository.findByBrojRacuna("111000110000000022")).thenReturn(Optional.of(to));

        InfoResponseDto result = service.info(null, "111000110000000011", "111000110000000022");

        assertThat(result.getFromCurrencyCode()).isEqualTo(CurrencyCode.RSD);
        assertThat(result.getToCurrencyCode()).isEqualTo(CurrencyCode.RSD);
        assertThat(result.getFromVlasnik()).isEqualTo(1L);
        assertThat(result.getToVlasnik()).isEqualTo(2L);
    }

    @Test
    void infoReturnsCorrectCurrenciesForDifferentCurrencies() {
        CheckingAccount from = checkingAccount("111000110000000011", 1L, RSD);
        FxAccount to = fxAccount("111000120000000021", 2L, EUR);

        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));
        when(accountRepository.findByBrojRacuna("111000120000000021")).thenReturn(Optional.of(to));

        InfoResponseDto result = service.info(null, "111000110000000011", "111000120000000021");

        assertThat(result.getFromCurrencyCode()).isEqualTo(CurrencyCode.RSD);
        assertThat(result.getToCurrencyCode()).isEqualTo(CurrencyCode.EUR);
    }

    @Test
    void infoThrowsWhenFromAccountNotFound() {
        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.info(null, "111000110000000011", "111000110000000022"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ne postoji from racun");
    }

    @Test
    void infoThrowsWhenFromAccountInactive() {
        CheckingAccount from = checkingAccount("111000110000000011", 1L, RSD);
        from.setStatus(Status.INACTIVE);
        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));

        assertThatThrownBy(() -> service.info(null, "111000110000000011", "111000110000000022"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FromAccount nije aktivan");
    }

    @Test
    void infoThrowsWhenToAccountNotFound() {
        CheckingAccount from = checkingAccount("111000110000000011", 1L, RSD);
        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));
        when(accountRepository.findByBrojRacuna("111000110000000022")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.info(null, "111000110000000011", "111000110000000022"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ne postoji to racun");
    }

    @Test
    void infoThrowsWhenToAccountInactive() {
        CheckingAccount from = checkingAccount("111000110000000011", 1L, RSD);
        CheckingAccount to = checkingAccount("111000110000000022", 2L, RSD);
        to.setStatus(Status.INACTIVE);
        when(accountRepository.findByBrojRacuna("111000110000000011")).thenReturn(Optional.of(from));
        when(accountRepository.findByBrojRacuna("111000110000000022")).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> service.info(null, "111000110000000011", "111000110000000022"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ToAccount nije aktivan");
    }

    // ──────────────────── helpers ────────────────────

    private CheckingAccount checkingAccount(String broj, long ownerId, Currency currency) {
        CheckingAccount ca = new CheckingAccount(AccountConcrete.STANDARDNI);
        ca.setBrojRacuna(broj);
        ca.setImeVlasnikaRacuna("Pera");
        ca.setPrezimeVlasnikaRacuna("Peric");
        ca.setNazivRacuna("Racun");
        ca.setVlasnik(ownerId);
        ca.setZaposlen(1L);
        ca.setCurrency(currency);
        ca.setDnevniLimit(new BigDecimal("250000"));
        ca.setMesecniLimit(new BigDecimal("1000000"));
        ca.setDnevnaPotrosnja(BigDecimal.ZERO);
        ca.setMesecnaPotrosnja(BigDecimal.ZERO);
        ca.setStanje(new BigDecimal("5000"));
        ca.setRaspolozivoStanje(new BigDecimal("5000"));
        return ca;
    }

    private FxAccount fxAccount(String broj, long ownerId, Currency currency) {
        FxAccount fa = new FxAccount(AccountOwnershipType.PERSONAL);
        fa.setBrojRacuna(broj);
        fa.setImeVlasnikaRacuna("Ana");
        fa.setPrezimeVlasnikaRacuna("Anic");
        fa.setNazivRacuna("FX racun");
        fa.setVlasnik(ownerId);
        fa.setZaposlen(1L);
        fa.setCurrency(currency);
        return fa;
    }

    private PaymentDto payment(String from, String to, String fromAmount, String toAmount,
                               String commission, Long clientId) {
        return new PaymentDto(from, to, new BigDecimal(fromAmount), new BigDecimal(toAmount),
                new BigDecimal(commission), clientId);
    }
}
