package com.banka1.order.entity.enums;

/**
 * Type of security held in a user's portfolio.
 * Determines which operations are available (e.g. options can be exercised, stocks can be made public).
 */
public enum ListingType {
    /** Equity share in a company. Can be made public for OTC trading. */
    STOCK,
    /** Futures contract — obligation to buy/sell an asset at a future date and price. */
    FUTURES,
    /** Foreign exchange pair (e.g. USD/RSD). Traded without commission. */
    FOREX,
    /** Options contract — right (not obligation) to buy/sell shares at a strike price. Only actuaries can hold these. */
    OPTION
}
