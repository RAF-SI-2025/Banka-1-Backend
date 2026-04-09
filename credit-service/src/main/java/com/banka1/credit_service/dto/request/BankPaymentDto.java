package com.banka1.credit_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BankPaymentDto {
    /**
     * Broj računa na koji se novac prenosi (19 cifara).
     */

    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;


}
