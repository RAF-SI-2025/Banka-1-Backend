package com.banka1.account_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "transaction_record_table"
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TransactionRecord extends BaseEntity{

    @NotBlank
    @Column(nullable = false,updatable = false)
    private String accountNumber;
    @NotBlank
    @Column(nullable = false,updatable = false)
    private String bankAccountNumber;
    @Column(nullable = false,updatable = false)
    private BigDecimal amount;

    public TransactionRecord(String accountNumber, String bankAccountNumber, BigDecimal amount) {
        this.accountNumber = accountNumber;
        this.bankAccountNumber = bankAccountNumber;
        this.amount = amount;
    }

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
