package com.banka1.order.entity.enums;

/**
 * Type of brokerage order, determining when and how it is executed.
 */
public enum OrderType {
    /** Executed immediately at the current market price. */
    MARKET,
    /** Executed only when the market price reaches or beats the specified limit value. */
    LIMIT,
    /** Becomes a market order when the market price reaches the specified stop value. */
    STOP,
    /** Becomes a limit order when the market price reaches the specified stop value. */
    STOP_LIMIT
}
