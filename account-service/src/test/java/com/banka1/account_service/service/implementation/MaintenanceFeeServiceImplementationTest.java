package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.CheckingAccount;
import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.TransactionRecord;
import com.banka1.account_service.domain.enums.AccountConcrete;
import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.domain.enums.Status;
import com.banka1.account_service.repository.AccountRepository;
import com.banka1.account_service.repository.CurrencyRepository;
import com.banka1.account_service.repository.TransactionRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceFeeServiceImplementationTest {

    @Mock private AccountRepository accountRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private TransactionRecordRepository transactionRecordRepository;

    @InjectMocks
    private MaintenanceFeeServiceImplementation service;

    private static final Currency RSD = new Currency("Dinar", CurrencyCode.RSD, "din", Set.of("RS"), "desc", Status.ACTIVE);

    @Test
    void processDeductsFeeAndCreditsBankAccount() {
        CheckingAccount client = clientAccount("111000110000000011", "1000", "255.00");
        CheckingAccount bank = bankAccount("111000110000000099", "50000");

        when(currencyRepository.findByOznaka(CurrencyCode.RSD)).thenReturn(Optional.of(RSD));
        when(accountRepository.findAllActiveCheckingAccountsWithMaintenanceFee()).thenReturn(List.of(client));
        when(accountRepository.findByVlasnikAndCurrency(-1L, RSD)).thenReturn(Optional.of(bank));

        service.process();

        assertThat(client.getStanje()).isEqualByComparingTo("745.00");
        assertThat(client.getRaspolozivoStanje()).isEqualByComparingTo("745.00");
        assertThat(bank.getStanje()).isEqualByComparingTo("50255.00");
        assertThat(bank.getRaspolozivoStanje()).isEqualByComparingTo("50255.00");
        verify(transactionRecordRepository).save(any(TransactionRecord.class));
    }

    @Test
    void processSkipsAccountWithInsufficientBalance() {
        CheckingAccount poor = clientAccount("111000110000000011", "100", "255.00");
        CheckingAccount bank = bankAccount("111000110000000099", "50000");

        when(currencyRepository.findByOznaka(CurrencyCode.RSD)).thenReturn(Optional.of(RSD));
        when(accountRepository.findAllActiveCheckingAccountsWithMaintenanceFee()).thenReturn(List.of(poor));
        when(accountRepository.findByVlasnikAndCurrency(-1L, RSD)).thenReturn(Optional.of(bank));

        service.process();

        // Account is unchanged
        assertThat(poor.getStanje()).isEqualByComparingTo("100");
        // Bank gets no credit
        assertThat(bank.getStanje()).isEqualByComparingTo("50000");
        verify(transactionRecordRepository, never()).save(any());
    }

    @Test
    void processSkipsAccountWithZeroFee() {
        CheckingAccount freeAccount = clientAccount("111000110000000011", "1000", "0.00");
        CheckingAccount bank = bankAccount("111000110000000099", "50000");

        when(currencyRepository.findByOznaka(CurrencyCode.RSD)).thenReturn(Optional.of(RSD));
        when(accountRepository.findAllActiveCheckingAccountsWithMaintenanceFee()).thenReturn(List.of(freeAccount));
        when(accountRepository.findByVlasnikAndCurrency(-1L, RSD)).thenReturn(Optional.of(bank));

        service.process();

        assertThat(freeAccount.getStanje()).isEqualByComparingTo("1000");
        assertThat(bank.getStanje()).isEqualByComparingTo("50000");
        verify(transactionRecordRepository, never()).save(any());
    }

    @Test
    void processSkipsAccountWithNullFee() {
        CheckingAccount nullFeeAccount = clientAccount("111000110000000011", "1000", "0");
        nullFeeAccount.setOdrzavanjeRacuna(null);
        CheckingAccount bank = bankAccount("111000110000000099", "50000");

        when(currencyRepository.findByOznaka(CurrencyCode.RSD)).thenReturn(Optional.of(RSD));
        when(accountRepository.findAllActiveCheckingAccountsWithMaintenanceFee()).thenReturn(List.of(nullFeeAccount));
        when(accountRepository.findByVlasnikAndCurrency(-1L, RSD)).thenReturn(Optional.of(bank));

        service.process();

        assertThat(bank.getStanje()).isEqualByComparingTo("50000");
        verify(transactionRecordRepository, never()).save(any());
    }

    @Test
    void processAggregatesFeeFromMultipleAccounts() {
        CheckingAccount c1 = clientAccount("111000110000000011", "2000", "255.00");
        CheckingAccount c2 = clientAccount("111000110000000022", "2000", "200.00");
        CheckingAccount bank = bankAccount("111000110000000099", "50000");

        when(currencyRepository.findByOznaka(CurrencyCode.RSD)).thenReturn(Optional.of(RSD));
        when(accountRepository.findAllActiveCheckingAccountsWithMaintenanceFee()).thenReturn(List.of(c1, c2));
        when(accountRepository.findByVlasnikAndCurrency(-1L, RSD)).thenReturn(Optional.of(bank));

        service.process();

        assertThat(c1.getStanje()).isEqualByComparingTo("1745.00");
        assertThat(c2.getStanje()).isEqualByComparingTo("1800.00");
        assertThat(bank.getStanje()).isEqualByComparingTo("50455.00");
        verify(transactionRecordRepository, times(2)).save(any());
    }

    @Test
    void processTransactionRecordHasCorrectAccountNumbers() {
        CheckingAccount client = clientAccount("111000110000000011", "1000", "255.00");
        CheckingAccount bank = bankAccount("111000110000000099", "50000");

        when(currencyRepository.findByOznaka(CurrencyCode.RSD)).thenReturn(Optional.of(RSD));
        when(accountRepository.findAllActiveCheckingAccountsWithMaintenanceFee()).thenReturn(List.of(client));
        when(accountRepository.findByVlasnikAndCurrency(-1L, RSD)).thenReturn(Optional.of(bank));

        ArgumentCaptor<TransactionRecord> recordCaptor = ArgumentCaptor.forClass(TransactionRecord.class);
        service.process();

        verify(transactionRecordRepository).save(recordCaptor.capture());
        TransactionRecord record = recordCaptor.getValue();
        assertThat(record.getAccountNumber()).isEqualTo("111000110000000011");
        assertThat(record.getBankAccountNumber()).isEqualTo("111000110000000099");
        assertThat(record.getAmount()).isEqualByComparingTo("255.00");
    }

    @Test
    void processThrowsWhenRsdCurrencyNotFound() {
        when(currencyRepository.findByOznaka(CurrencyCode.RSD)).thenReturn(Optional.empty());
        when(accountRepository.findAllActiveCheckingAccountsWithMaintenanceFee()).thenReturn(List.of());

        assertThatThrownBy(() -> service.process())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ne postoji RSD currency");
    }

    @Test
    void processThrowsWhenBankAccountNotFound() {
        CheckingAccount client = clientAccount("111000110000000011", "1000", "255.00");

        when(currencyRepository.findByOznaka(CurrencyCode.RSD)).thenReturn(Optional.of(RSD));
        when(accountRepository.findAllActiveCheckingAccountsWithMaintenanceFee()).thenReturn(List.of(client));
        when(accountRepository.findByVlasnikAndCurrency(-1L, RSD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Bank RSD account not found");
    }

    // ──────────────────── helpers ────────────────────

    private CheckingAccount clientAccount(String broj, String stanje, String fee) {
        CheckingAccount ca = new CheckingAccount(AccountConcrete.STANDARDNI);
        ca.setBrojRacuna(broj);
        ca.setImeVlasnikaRacuna("Pera");
        ca.setPrezimeVlasnikaRacuna("Peric");
        ca.setNazivRacuna("Tekuci");
        ca.setVlasnik(5L);
        ca.setZaposlen(1L);
        ca.setCurrency(RSD);
        ca.setStanje(new BigDecimal(stanje));
        ca.setRaspolozivoStanje(new BigDecimal(stanje));
        ca.setOdrzavanjeRacuna(new BigDecimal(fee));
        return ca;
    }

    private CheckingAccount bankAccount(String broj, String stanje) {
        CheckingAccount bank = new CheckingAccount(AccountConcrete.STANDARDNI);
        bank.setBrojRacuna(broj);
        bank.setImeVlasnikaRacuna("Banka");
        bank.setPrezimeVlasnikaRacuna("Banka");
        bank.setNazivRacuna("Bank RSD");
        bank.setVlasnik(-1L);
        bank.setZaposlen(1L);
        bank.setCurrency(RSD);
        bank.setStanje(new BigDecimal(stanje));
        bank.setRaspolozivoStanje(new BigDecimal(stanje));
        return bank;
    }
}
