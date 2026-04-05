package com.banka1.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records a single executed portion of an order.
 * Orders are filled incrementally — each partial fill produces one Transaction.
 * For All-or-None orders, there is exactly one transaction per order.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference to the parent order. */
    @Column(nullable = false)
    private Long orderId;

    /** Number of units filled in this portion. */
    @Column(nullable = false)
    private Integer quantity;

    /** Price per unit at the time of execution. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal pricePerUnit;

    /** Total value of this portion: {@code quantity * pricePerUnit}. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPrice;

    /** Commission charged for this portion, transferred to the bank's account. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal commission;

    /** Timestamp when this portion was executed. Set automatically on first persist. */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /** Sets the execution timestamp to now if not already assigned. */
    @PrePersist
    public void setTimestamp() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }
}
