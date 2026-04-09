package com.banka1.credit_service.domain;

import com.banka1.credit_service.domain.enums.CurrencyCode;
import com.banka1.credit_service.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "installment_table",
        indexes = {
                @Index(name = "idx_installment_loan_id", columnList = "loan_id")
        }
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Installment extends BaseEntity{
    //todo proveriti da li lazy
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;
    @Column(nullable = false)
    private BigDecimal installmentAmount;
    @Column(nullable = false)
    private BigDecimal interestRateAtPayment;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurrencyCode currency;
    @Column(nullable = false)
    private LocalDate expectedDueDate;
    @Column
    private LocalDate actualDueDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;
    @Column(nullable = false)
    private int retry=0;

    public Installment(Loan loan, BigDecimal installmentAmount, BigDecimal interestRateAtPayment, CurrencyCode currency, LocalDate expectedDueDate, LocalDate actualDueDate, PaymentStatus paymentStatus) {
        this.loan = loan;
        this.installmentAmount = installmentAmount;
        this.interestRateAtPayment = interestRateAtPayment;
        this.currency = currency;
        this.expectedDueDate = expectedDueDate;
        this.actualDueDate = actualDueDate;
        this.paymentStatus = paymentStatus;
    }
}
