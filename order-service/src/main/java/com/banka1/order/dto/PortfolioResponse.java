package com.banka1.order.dto;

import com.banka1.order.entity.enums.ListingType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO representing a user's portfolio position returned by the API.
 *
 * This object is used by the frontend to display:
 * - current holdings
 * - market value
 * - profit/loss information
 * - OTC visibility (for stocks)
 *
 * NOTE:
 * Values like currentPrice and profit are computed at runtime
 * using data from stock-service.
 */
@Data
public class PortfolioResponse {

    /** Type of security (STOCK, OPTION, etc.). */
    private ListingType listingType;

    /**
     * Ticker symbol of the security (e.g. AAPL, MSFT).
     * Fetched from stock-service based on listingId.
     */
    private String ticker;

    /** Number of units currently held. */
    private Integer quantity;

    /** Number of units available for OTC trading (stocks only). */
    private Integer publicQuantity;

    /** Whether the option can currently be exercised. Null for non-option holdings. */
    private Boolean exercisable;

    /** Last time this portfolio position was modified. */
    private LocalDateTime lastModified;

    /** Current market price fetched from stock-service. */
    private BigDecimal currentPrice;

    /** Average acquisition price stored on the portfolio position. */
    private BigDecimal averagePurchasePrice;

    /** Unrealized or realized profit for this position. */
    private BigDecimal profit;

}
