package com.banka1.order.entity;

import com.banka1.order.entity.enums.TaxChargeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "tax_charges",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tax_charges_sell_buy",
                columnNames = {"sell_transaction_id", "buy_transaction_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class TaxCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sell_transaction_id", nullable = false)
    private Long sellTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sell_transaction_id", insertable = false, updatable = false)
    private Transaction sellTransaction;

    @Column(name = "buy_transaction_id", nullable = false)
    private Long buyTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buy_transaction_id", insertable = false, updatable = false)
    private Transaction buyTransaction;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long listingId;

    @Column(nullable = false)
    private Long sourceAccountId;

    @Column(nullable = false)
    private LocalDateTime taxPeriodStart;

    @Column(nullable = false)
    private LocalDateTime taxPeriodEnd;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(precision = 19, scale = 4)
    private BigDecimal taxAmountRsd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaxChargeStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime chargedAt;

    @PrePersist
    public void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
