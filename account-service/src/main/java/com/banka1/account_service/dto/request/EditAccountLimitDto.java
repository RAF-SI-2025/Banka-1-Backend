package com.banka1.account_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
    @NotNull(message = "Unesi koji limit")
    private TipLimita tipLimita;
    @NotBlank(message = "Unesi kod za verifikaciju")
    private String verificationCode;
    @NotNull(message = "Unesi verification session ID")
    private Long verificationSessionId;


    public enum TipLimita{
        MESECNI,DNEVNI
    }
}
