package com.banka1.account_service.util;

import com.banka1.account_service.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountNumberGeneratorTest {

    private static class StubRandom extends Random {
        private final int[] values;
        private int index = 0;

        private StubRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            return values[index++];
        }
    }

    @Mock
    private AccountRepository accountRepository;

    @Test
    void generateReturns19DigitNumber() {
        when(accountRepository.existsByBrojRacuna(anyString())).thenReturn(false);

        String number = AccountNumberGenerator.generate("11", new Random(42), accountRepository);

        assertThat(number).hasSize(19).matches("\\d{19}");
    }

    @Test
    void generateStartsWithBankAndBranchCode() {
        when(accountRepository.existsByBrojRacuna(anyString())).thenReturn(false);

        String number = AccountNumberGenerator.generate("21", new Random(1), accountRepository);

        assertThat(number).startsWith("1110001");
    }

    @Test
    void generateEndsWithProvidedTypeCode() {
        when(accountRepository.existsByBrojRacuna(anyString())).thenReturn(false);

        String number = AccountNumberGenerator.generate("22", new Random(1), accountRepository);

        assertThat(number.substring(16, 18)).isEqualTo("22");
    }

    @Test
    void generateRetriesOnCollision() {
        // First two calls return true (collision), third returns false (unique)
        when(accountRepository.existsByBrojRacuna(anyString()))
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);

        String number = AccountNumberGenerator.generate("11", new Random(99), accountRepository);

        assertThat(number).hasSize(19);
    }

    @Test
    void calculateCheckDigitProducesExpectedDigit() {
        String prefix = "111000100000000011";

        int checkDigit = AccountNumberGenerator.calculateCheckDigit(prefix);

        assertThat(checkDigit).isEqualTo(5);
    }

    @Test
    void generateSkipsCandidateWhenCheckDigitIsTen() {
        when(accountRepository.existsByBrojRacuna(anyString())).thenReturn(false);

        // First 9 digits sum to 6 -> checksum 10 (skip), second 9 digits all 0 -> checksum 5 (valid)
        Random random = new StubRandom(
                6, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0
        );

        String number = AccountNumberGenerator.generate("11", random, accountRepository);

        assertThat(number).isEqualTo("1110001000000000115");
    }

    @Test
    void validateAccountNumberReturnsTrueForValidNumber() {
        when(accountRepository.existsByBrojRacuna(anyString())).thenReturn(false);

        String number = AccountNumberGenerator.generate("11", new Random(7), accountRepository);

        assertThat(AccountNumberGenerator.validateAccountNumber(number)).isTrue();
    }

    @Test
    void validateAccountNumberReturnsFalseForNull() {
        assertThat(AccountNumberGenerator.validateAccountNumber(null)).isFalse();
    }

    @Test
    void validateAccountNumberReturnsFalseForWrongLength() {
        assertThat(AccountNumberGenerator.validateAccountNumber("12345")).isFalse();
        assertThat(AccountNumberGenerator.validateAccountNumber("123456789012345678")).isFalse();
    }

    @Test
    void validateAccountNumberReturnsFalseForNonDigits() {
        assertThat(AccountNumberGenerator.validateAccountNumber("11100011234567AB119")).isFalse();
    }

    @Test
    void validateAccountNumberReturnsFalseForWrongPrefix() {
        assertThat(AccountNumberGenerator.validateAccountNumber("2220001100000000119")).isFalse();
    }

    @Test
    void validateAccountNumberReturnsFalseForUnknownTypeCode() {
        assertThat(AccountNumberGenerator.validateAccountNumber("1110001123456788999")).isFalse();
    }

    @Test
    void validateAccountNumberReturnsFalseForWrongCheckDigit() {
        when(accountRepository.existsByBrojRacuna(anyString())).thenReturn(false);
        String valid = AccountNumberGenerator.generate("11", new Random(5), accountRepository);

        int lastDigit = valid.charAt(18) - '0';
        int wrongDigit = (lastDigit + 1) % 10;
        String corrupted = valid.substring(0, 18) + wrongDigit;

        assertThat(AccountNumberGenerator.validateAccountNumber(corrupted)).isFalse();
    }
}
