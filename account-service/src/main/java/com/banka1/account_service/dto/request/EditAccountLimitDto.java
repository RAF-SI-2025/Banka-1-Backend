package com.banka1.account_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class EditAccountLimitDto {
    @NotNull(message = "Unesi limit racuna")
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal accountLimit;
}
