package com.banka1.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO representing a security listing as returned by the stock-service.
 */
@Data
public class StockListingDto {
    /** The listing's unique identifier. */
    private Long id;
    /** Ticker symbol (e.g. "AAPL", "MSFT"). */
    private String ticker;
    /** Full name of the security. */
    private String name;
    /** Last traded price. */
    private BigDecimal price;
    /** Currency code of the listing's exchange. */
    private String currency;
    /** Identifier of the exchange this listing belongs to. */
    private Long exchangeId;
    /** Number of units per contract. */
    private Integer contractSize;
}
