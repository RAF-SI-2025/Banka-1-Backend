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
public class PaymentDto {
    @NotBlank(message = "Unesi racun posiljaoca")
    @Pattern(regexp = "^\\d{18}$", message = "Broj racuna mora imati 18 cifara")
    private String fromAccountNumber;
    @NotBlank(message = "Unesi racun primaoca")
    @Pattern(regexp = "^\\d{18}$", message = "Broj racuna mora imati 18 cifara")
    private String toAccountNumber;
    @NotNull(message = "Unesi iznos pre konverzije")
    private BigDecimal fromAmount;
    @NotNull(message = "Unesi iznos posle konverzije")
    private BigDecimal toAmount;
    @NotNull
    @DecimalMin(value = "0.00",message = "Minimalni commission je 0")
    private BigDecimal commission;

    private Long clientId;
}
