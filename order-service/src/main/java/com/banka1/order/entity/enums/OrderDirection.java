package com.banka1.order.entity.enums;

/**
 * Indicates whether an order is a purchase or a sale.
 */
public enum OrderDirection {
    /** The user wants to buy a security. */
    BUY,
    /** The user wants to sell a security they already hold. */
    SELL
}
