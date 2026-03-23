package com.banka1.account_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TransactionDto {
    @NotNull(message = "Unesi amount")
    private BigDecimal amount;
    @NotBlank(message = "Unesi referenceType")
    private String referenceType;
    @NotBlank(message = "Unesi referenceId")
    private String referenceId;
}
