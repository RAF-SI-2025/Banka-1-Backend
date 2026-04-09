package com.banka1.account_service.dto.request;

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
    @Pattern(regexp = "^\\d{19}$", message = "Broj racuna mora imati 19 cifara")
    private String fromAccountNumber;


    @Pattern(regexp = "^\\d{19}$", message = "Broj racuna mora imati 19 cifara")
    private String toAccountNumber;


    @DecimalMin(value = "0.0", inclusive = false, message = "Iznos pre konverzije mora biti veci od 0")
    private BigDecimal amount;

}
