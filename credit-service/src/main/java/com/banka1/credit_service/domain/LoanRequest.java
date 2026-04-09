package com.banka1.credit_service.domain;

import com.banka1.credit_service.domain.enums.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "loan_request_table",
        indexes = {
        @Index(name = "idx_loan_request_client_id", columnList = "client_id")
        }
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class LoanRequest extends BaseEntity{
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanType loanType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterestType interestType;
    //todo proveriti da li je positive ili ne
    @Positive
    @Column(nullable = false)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurrencyCode currency;
    @NotBlank
    @Column(nullable = false)
    private String purpose;
    @Column(nullable = false)
    private BigDecimal monthlySalary;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentStatus employmentStatus;
    @Column(nullable = false)
    private Integer currentEmploymentPeriod;
    @Positive
    @Column(nullable = false)
    private Integer repaymentPeriod;
    //todo validacija za telefon mozda
    @NotBlank
    @Column(nullable = false)
    private String contactPhone;
    @NotBlank
    @Column(nullable = false)
    private String accountNumber;
    @Column(nullable = false)
    private Long clientId;
    //todo videti da li je samo PENDING,APPROVED I DECLINED ILI I ONI KOJI SU U LOANU
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @Column(nullable = false)
    private String userEmail;
    @Column(nullable = false)
    private String username;


}
