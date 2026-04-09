package com.banka1.order.entity;

import com.banka1.order.entity.enums.ListingType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a single security position held by a user.
 * Tracks quantity, average purchase price, and optional OTC visibility (stocks only).
 * Each record is identified by the combination of userId and listingId.
 */
@Entity
@Table(name = "portfolio",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "listing_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the user (client) who owns this position. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** ID of the security listing in stock-service. */
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    /** Type of the held security (STOCK, FUTURES, FOREX, OPTION). */
    @Enumerated(EnumType.STRING)
    @Column(name = "listing_type", nullable = false)
    private ListingType listingType;

    /** Total number of units currently held. */
    @Column(nullable = false)
    private Integer quantity;

    /** Units reserved by confirmed sell orders and unavailable for new sell reservations. */
    @Column(nullable = false)
    private Integer reservedQuantity = 0;

    /** Weighted average price at which units were purchased, in RSD. */
    @Column(name = "average_purchase_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal averagePurchasePrice;

    /**
     * Whether this position is publicly visible for OTC trading.
     * Only applicable to STOCK positions; always false for other types.
     */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    /**
     * Number of units the owner has made available for OTC trading.
     * Meaningful only when {@code isPublic} is true.
     */
    @Column(name = "public_quantity", nullable = false)
    private Integer publicQuantity = 0;

    /** Timestamp of the last change to this position. Updated automatically on every save. */
    @Column(name = "last_modified", nullable = false)
    private LocalDateTime lastModified;

    /** Updates the lastModified timestamp on every persist and update. */
    @PrePersist
    @PreUpdate
    public void updateLastModified() {
        this.lastModified = LocalDateTime.now();
    }
}
