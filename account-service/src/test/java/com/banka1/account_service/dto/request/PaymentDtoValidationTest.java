package com.banka1.account_service.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentDtoValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        validatorFactory.close();
    }

    @Test
    void accountNumbersAccept18DigitsAndReject19Digits() {
        PaymentDto valid = validDto();
        Set<ConstraintViolation<PaymentDto>> validViolations = validator.validate(valid);
        assertThat(validViolations).isEmpty();

        PaymentDto invalid = validDto();
        invalid.setFromAccountNumber("1110001000000000115");
        Set<ConstraintViolation<PaymentDto>> violations = validator.validate(invalid);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("fromAccountNumber");
    }

    @Test
    void fromAmountRejectsZeroAndNegative() {
        PaymentDto zero = validDto();
        zero.setFromAmount(BigDecimal.ZERO);

        Set<ConstraintViolation<PaymentDto>> zeroViolations = validator.validate(zero);
        assertThat(zeroViolations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("fromAmount");

        PaymentDto negative = validDto();
        negative.setFromAmount(new BigDecimal("-1"));

        Set<ConstraintViolation<PaymentDto>> negativeViolations = validator.validate(negative);
        assertThat(negativeViolations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("fromAmount");
    }

    @Test
    void toAmountRejectsZeroAndNegative() {
        PaymentDto zero = validDto();
        zero.setToAmount(BigDecimal.ZERO);

        Set<ConstraintViolation<PaymentDto>> zeroViolations = validator.validate(zero);
        assertThat(zeroViolations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("toAmount");

        PaymentDto negative = validDto();
        negative.setToAmount(new BigDecimal("-1"));

        Set<ConstraintViolation<PaymentDto>> negativeViolations = validator.validate(negative);
        assertThat(negativeViolations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("toAmount");
    }

    @Test
    void commissionAllowsZeroAndRejectsNegative() {
        PaymentDto zeroCommission = validDto();
        zeroCommission.setCommission(BigDecimal.ZERO);

        Set<ConstraintViolation<PaymentDto>> zeroViolations = validator.validate(zeroCommission);
        assertThat(zeroViolations).isEmpty();

        PaymentDto negativeCommission = validDto();
        negativeCommission.setCommission(new BigDecimal("-0.01"));

        Set<ConstraintViolation<PaymentDto>> negativeViolations = validator.validate(negativeCommission);
        assertThat(negativeViolations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("commission");
    }

    private PaymentDto validDto() {
        return new PaymentDto(
                "111000100000000011",
                "111000100000000012",
                new BigDecimal("100.00"),
                new BigDecimal("99.00"),
                BigDecimal.ZERO,
                42L
        );
    }
}

