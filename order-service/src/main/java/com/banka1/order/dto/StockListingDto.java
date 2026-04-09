package com.banka1.order.dto;

import com.banka1.order.entity.enums.ListingType;
import com.banka1.order.entity.enums.OptionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    /** Ask price. */
    private BigDecimal ask;
    /** Bid price. */
    private BigDecimal bid;
    /** Currency code of the listing's exchange. */
    private String currency;
    /** Identifier of the exchange this listing belongs to. */
    private Long exchangeId;
    /** Number of units per contract. */
    private Integer contractSize;
    /** Listing category used for margin and portfolio handling. */
    private ListingType listingType;
    /** Current market volume available for simulation purposes. */
    private Long volume;
    /** Settlement date, when provided by stock-service. */
    private LocalDate settlementDate;
    /** Optional identifier of the underlying listing for option exercise. */
    private Long underlyingListingId;
    /** Optional strike price for option listings. */
    private BigDecimal strikePrice;
    /** Optional option type for option listings. */
    private OptionType optionType;
    /** Optional underlying spot price used for option margin calculation. */
    private BigDecimal underlyingPrice;
    /** Optional maintenance margin coming directly from stock-service. */
    private BigDecimal maintenanceMargin;
}
