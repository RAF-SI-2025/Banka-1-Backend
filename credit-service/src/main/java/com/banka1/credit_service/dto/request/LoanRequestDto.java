package com.banka1.credit_service.dto.request;

import com.banka1.credit_service.domain.enums.*;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class LoanRequestDto {
    @NotNull(message = "loanType ne sme biti null")
    private LoanType loanType;
    @NotNull(message = "interestType ne sme biti null")
    private InterestType interestType;
    //todo proveriti da li je positive ili ne
    @Positive(message = "amount mora biti >0")
    @NotNull(message = "amount ne sme biti null")
    private BigDecimal amount;
    @NotNull(message = "currency ne sme biti null")
    private CurrencyCode currency;
    @NotBlank(message = "purpose ne sme biti prazan")
    private String purpose;
    @NotNull(message = "monthlySalary ne sme biti null")
    private BigDecimal monthlySalary;
    @NotNull(message = "employmentStatus ne sme biti null")
    private EmploymentStatus employmentStatus;
    @NotNull(message = "currentEmploymentPeriod ne sme biti null")
    private Integer currentEmploymentPeriod;
    @Positive(message = "repaymentPeriod mora biti pozitivan")
    @NotNull(message = "repaymentPeriod ne sme biti null")
    private Integer repaymentPeriod;
    //todo validacija za telefon mozda
    @NotBlank(message = "contactPhone ne sme biti prazan")
    private String contactPhone;
    @NotBlank(message = "accountNumber ne sme biti prazan")
    private String accountNumber;
    @NotNull(message = "clientId ne sme biti null")
    private Long clientId;

}
