package com.banka1.order.entity;

import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.entity.enums.OrderStatus;
import com.banka1.order.entity.enums.OrderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a brokerage order placed by an actuary or client.
 * Supports four order types (MARKET, LIMIT, STOP, STOP_LIMIT) in BUY or SELL direction.
 * Orders may require supervisor approval depending on the agent's settings and limit usage.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the actuary or client who placed the order. */
    @Column(nullable = false)
    private Long userId;

    /** ID of the security listing in stock-service. */
    @Column(nullable = false)
    private Long listingId;

    /** Type of the order: MARKET, LIMIT, STOP, or STOP_LIMIT. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    /** Number of securities to buy or sell per contract. */
    @Column(nullable = false)
    private Integer quantity;

    /** Number of units per contract for this listing. */
    @Column(nullable = false)
    private Integer contractSize;

    /** Reference or actual price per unit depending on order type. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal pricePerUnit;

    /** Activation price for LIMIT and STOP_LIMIT orders. Null for MARKET and STOP. */
    @Column(precision = 19, scale = 4)
    private BigDecimal limitValue;

    /** Activation price for STOP and STOP_LIMIT orders. Null for MARKET and LIMIT. */
    @Column(precision = 19, scale = 4)
    private BigDecimal stopValue;

    /** Whether this is a BUY or SELL order. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderDirection direction;

    /** Current lifecycle status of the order. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /** ID of the supervisor who approved or declined the order. Null if no approval was needed. */
    private Long approvedBy;

    /** True when all portions of the order have been executed. */
    @Column(nullable = false)
    private Boolean isDone = false;

    /** Timestamp of the last status change (approval, decline, execution, etc.). */
    @Column(nullable = false)
    private LocalDateTime lastModification;

    /** Number of units still to be executed. Decreases as partial fills occur. */
    @Column(nullable = false)
    private Integer remainingPortions;

    /** True if the order was placed within 4 hours of the exchange closing. */
    @Column(nullable = false)
    private Boolean afterHours;

    /** True if the exchange was closed when the order was last validated/confirmed. */
    @Column(nullable = false)
    private Boolean exchangeClosed = false;

    /** If true, the order must be filled completely or not at all (All-or-None). */
    @Column(nullable = false)
    private Boolean allOrNone;

    /** If true, the order uses margin (borrowed funds). Requires appropriate permissions. */
    @Column(nullable = false)
    private Boolean margin;

    /** ID of the bank account from which funds will be debited or credited. */
    @Column(nullable = false)
    private Long accountId;

    /** Commission-free RSD exposure reserved for pending/approved agent orders. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedLimitExposure = BigDecimal.ZERO;

    /** Updates the lastModification timestamp on every persist and update. */
    @PrePersist
    @PreUpdate
    public void updateLastModification() {
        this.lastModification = LocalDateTime.now();
    }
}
