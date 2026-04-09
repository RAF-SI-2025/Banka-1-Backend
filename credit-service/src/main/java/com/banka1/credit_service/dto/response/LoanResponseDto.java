package com.banka1.credit_service.dto.response;

import com.banka1.credit_service.domain.Loan;
import com.banka1.credit_service.domain.enums.InterestType;
import com.banka1.credit_service.domain.enums.LoanType;
import com.banka1.credit_service.domain.enums.Status;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoanResponseDto {
    private Long loanNumber;
    private LoanType loanType;
    private String accountNumber;
    private BigDecimal amount;
    private Integer repaymentMethod;
    private BigDecimal nominalInterestRate;
    private BigDecimal effectiveInterestRate;
    private InterestType interestType;
    private LocalDate agreementDate;
    private LocalDate maturityDate;
    private BigDecimal installmentAmount;
    private LocalDate nextInstallmentDate;
    private BigDecimal remainingDebt;
    private Status status;

    public LoanResponseDto(Long loanNumber, LoanType loanType, BigDecimal amount, Status status) {
        this.loanNumber = loanNumber;
        this.loanType = loanType;
        this.amount = amount;
        this.status = status;
    }
    public LoanResponseDto(Loan loan)
    {
        this.loanNumber=loan.getId();
        this.loanType=loan.getLoanType();
        this.accountNumber=loan.getAccountNumber();
        this.amount=loan.getAmount();
        this.repaymentMethod=loan.getRepaymentPeriod();
        this.nominalInterestRate=loan.getNominalInterestRate();
        this.effectiveInterestRate=loan.getEffectiveInterestRate();
        this.interestType=loan.getInterestType();
        this.agreementDate=loan.getAgreementDate();
        this.maturityDate=loan.getMaturityDate();
        this.installmentAmount=loan.getInstallmentAmount();
        this.nextInstallmentDate=loan.getNextInstallmentDate();
        this.remainingDebt=loan.getRemainingDebt();
        this.status=loan.getStatus();
    }

}
