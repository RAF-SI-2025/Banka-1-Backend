package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.CheckingAccount;
import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.FxAccount;
import com.banka1.account_service.domain.enums.AccountConcrete;
import com.banka1.account_service.domain.enums.AccountOwnershipType;
import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.dto.request.PaymentDto;
import com.banka1.account_service.dto.response.UpdatedBalanceResponseDto;
import com.banka1.account_service.exception.BusinessException;
import com.banka1.account_service.exception.ErrorCode;
import com.banka1.account_service.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionalServiceImplementationTest {

    @Mock private AccountRepository accountRepository;

    @InjectMocks
    private TransactionalServiceImplementation service;

    private static final Currency RSD = new Currency("Dinar", CurrencyCode.RSD, "din", Set.of("RS"), "desc", Status.ACTIVE);
    private static final Currency EUR = new Currency("Euro", CurrencyCode.EUR, "€", Set.of("EU"), "desc", Status.ACTIVE);

    // ──────────────────── debit ────────────────────

    @Test
    void debitReducesBalanceAndTracksSpendings() {
        // dnevniLimit=500, dnevnaPotrosnja=0, mesecniLimit=1000, mesecnaPotrosnja=0
        CheckingAccount acc = account("111000110000000011", 1L, RSD, "1000", "1000", "0", "0");

        service.debit(acc, new BigDecimal("200"));

        assertThat(acc.getStanje()).isEqualByComparingTo("800");
        assertThat(acc.getRaspolozivoStanje()).isEqualByComparingTo("800");
        assertThat(acc.getDnevnaPotrosnja()).isEqualByComparingTo("200");
        assertThat(acc.getMesecnaPotrosnja()).isEqualByComparingTo("200");
        verify(accountRepository).save(acc);
    }

    @Test
    void debitThrowsWhenInsufficientFunds() {
        CheckingAccount acc = account("111000110000000011", 1L, RSD, "100", "100", "0", "0");

        assertThatThrownBy(() -> service.debit(acc, new BigDecimal("200")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));

        verify(accountRepository, never()).save(any());
    }

    @Test
    void debitThrowsWhenDailyLimitExceeded() {
        // raspolozivo=10000 (enough), but dnevnaPotrosnja=400+200=600 > dnevniLimit=500
        CheckingAccount acc = account("111000110000000011", 1L, RSD, "10000", "10000", "400", "0");

        assertThatThrownBy(() -> service.debit(acc, new BigDecimal("200")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DAILY_LIMIT_EXCEEDED));

        verify(accountRepository, never()).save(any());
    }

    @Test
    void debitThrowsWhenMonthlyLimitExceeded() {
        // daily is fine (0+150<500), but monthly: 900+150=1050 > 1000
        CheckingAccount acc = account("111000110000000011", 1L, RSD, "10000", "10000", "0", "900");

        assertThatThrownBy(() -> service.debit(acc, new BigDecimal("150")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MONTHLY_LIMIT_EXCEEDED));

        verify(accountRepository, never()).save(any());
    }

    // ──────────────────── credit ────────────────────

    @Test
    void creditIncreasesBalance() {
        CheckingAccount acc = account("111000110000000011", 1L, RSD, "500", "500", "0", "0");

        service.credit(acc, new BigDecimal("300"));

        assertThat(acc.getStanje()).isEqualByComparingTo("800");
        assertThat(acc.getRaspolozivoStanje()).isEqualByComparingTo("800");
        verify(accountRepository).save(acc);
    }

    // ──────────────────── transfer – same currency ────────────────────

    @Test
    void transferSameCurrencyDebitsFromAndCreditsTo() {
        CheckingAccount from = account("111000110000000011", 1L, RSD, "1000", "1000", "0", "0");
        CheckingAccount to = account("111000110000000022", 2L, RSD, "500", "500", "0", "0");
        CheckingAccount bankFrom = account("111000110000000099", -1L, RSD, "99999", "99999", "0", "0");
        CheckingAccount bankTo = bankFrom;

        PaymentDto dto = payment("111000110000000011", "111000110000000022", "200", "200", "0", 1L);

        UpdatedBalanceResponseDto result = service.transfer(from, to, bankFrom, bankTo, dto);

        assertThat(from.getStanje()).isEqualByComparingTo("800");
        assertThat(to.getStanje()).isEqualByComparingTo("700");
        // Bank account is untouched for same-currency transfers
        assertThat(bankFrom.getStanje()).isEqualByComparingTo("99999");

        assertThat(result.getSenderBalance()).isEqualByComparingTo("800");
        assertThat(result.getReceiverBalance()).isEqualByComparingTo("700");
    }

    @Test
    void transferDifferentCurrenciesRoutesViaBank() {
        CheckingAccount from = account("111000110000000011", 1L, RSD, "1000", "1000", "0", "0");
        FxAccount to = fxAccount("111000120000000021", 2L, EUR, "500", "500");
        CheckingAccount bankSender = account("111000110000000099", -1L, RSD, "50000", "50000", "0", "0");
        FxAccount bankTarget = fxAccount("111000120000000099", -1L, EUR, "50000", "50000");

        // from pays 200 RSD, to receives 1.85 EUR, commission 0.10 EUR
        PaymentDto dto = payment("111000110000000011", "111000120000000021", "200", "1.95", "0.10", 1L);

        UpdatedBalanceResponseDto result = service.transfer(from, to, bankSender, bankTarget, dto);

        // from debited 200 RSD
        assertThat(from.getStanje()).isEqualByComparingTo("800");
        // bankSender credited 200 RSD
        assertThat(bankSender.getStanje()).isEqualByComparingTo("50200");
        // bankTarget debited 1.95 EUR
        assertThat(bankTarget.getStanje()).isEqualByComparingTo("49998.05");
        // to credited (toAmount - commission) = 1.95 - 0.10 = 1.85 EUR
        assertThat(to.getStanje()).isEqualByComparingTo("501.85");
    }

    // ──────────────────── helpers ────────────────────

    /**
     * Creates a CheckingAccount with given balance/spending values.
     *
     * @param dnevniLimit  "dnevniLimit" value as string (used as dnevniLimit AND mesecniLimit to keep things simple)
     * @param dnevnaPotrosnja tracks existing daily spending
     */
    private CheckingAccount account(String broj, long ownerId, Currency currency,
                                    String stanje, String raspolozivo,
                                    String dnevnaPotrosnja, String mesecnaPotrosnja) {
        CheckingAccount ca = new CheckingAccount(AccountConcrete.STANDARDNI);
        ca.setBrojRacuna(broj);
        ca.setImeVlasnikaRacuna("Pera");
        ca.setPrezimeVlasnikaRacuna("Peric");
        ca.setNazivRacuna("Racun");
        ca.setVlasnik(ownerId);
        ca.setZaposlen(1L);
        ca.setCurrency(currency);
        ca.setStanje(new BigDecimal(stanje));
        ca.setRaspolozivoStanje(new BigDecimal(raspolozivo));
        ca.setDnevniLimit(new BigDecimal("500"));
        ca.setMesecniLimit(new BigDecimal("1000"));
        ca.setDnevnaPotrosnja(new BigDecimal(dnevnaPotrosnja));
        ca.setMesecnaPotrosnja(new BigDecimal(mesecnaPotrosnja));
        return ca;
    }

    private FxAccount fxAccount(String broj, long ownerId, Currency currency,
                                String stanje, String raspolozivo) {
        FxAccount fa = new FxAccount(AccountOwnershipType.PERSONAL);
        fa.setBrojRacuna(broj);
        fa.setImeVlasnikaRacuna("Ana");
        fa.setPrezimeVlasnikaRacuna("Anic");
        fa.setNazivRacuna("FX Racun");
        fa.setVlasnik(ownerId);
        fa.setZaposlen(1L);
        fa.setCurrency(currency);
        fa.setStanje(new BigDecimal(stanje));
        fa.setRaspolozivoStanje(new BigDecimal(raspolozivo));
        fa.setDnevniLimit(new BigDecimal("5000"));
        fa.setMesecniLimit(new BigDecimal("20000"));
        fa.setDnevnaPotrosnja(BigDecimal.ZERO);
        fa.setMesecnaPotrosnja(BigDecimal.ZERO);
        return fa;
    }

    private PaymentDto payment(String from, String to, String fromAmount, String toAmount,
                               String commission, Long clientId) {
        return new PaymentDto(from, to, new BigDecimal(fromAmount), new BigDecimal(toAmount),
                new BigDecimal(commission), clientId);
    }
}
