package com.banka1.credit_service.domain;

import com.banka1.credit_service.domain.enums.CurrencyCode;
import com.banka1.credit_service.domain.enums.InterestType;
import com.banka1.credit_service.domain.enums.LoanType;
import com.banka1.credit_service.domain.enums.Status;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "loan_table",
        indexes = {
        @Index(name = "idx_loan_account_number", columnList = "account_number")
})
@NoArgsConstructor
@Getter
@Setter

///napomena, loanNumber je ovde id, da bi i dalje nasledjivao base entity
///front ce videti to kao loanNumber ali ce se interno cuvati kao loanId


public class Loan extends BaseEntity{
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanType loanType;
    @NotBlank
    @Column(nullable = false)
    private String accountNumber;
    //todo pogledati sta je ovo
    //@Column(nullable = false,unique = true)
    //private Long loanNumber;
    @Positive
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    private Integer repaymentPeriod;
    @Column(nullable = false)
    private BigDecimal nominalInterestRate;
    @Column(nullable = false)
    private BigDecimal effectiveInterestRate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterestType interestType;
    @Column(nullable = false)
    private LocalDate agreementDate;
    @Column(nullable = false)
    private LocalDate maturityDate;
    @Column(nullable = false)
    private BigDecimal installmentAmount;
    @Column(nullable = false)
    private LocalDate nextInstallmentDate;
    @Column(nullable = false)
    private BigDecimal remainingDebt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurrencyCode currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @Column(nullable = false)
    private String userEmail;
    @Column(nullable = false)
    private String username;
    @Column(nullable = false)
    private Long clientId;
    @Column(nullable = false)
    private int installmentCount=0;
    @OneToMany(mappedBy = "loan", fetch = FetchType.LAZY)
    private List<Installment> installments;




    //todo dodati listu installmenta ako mi treba




}
