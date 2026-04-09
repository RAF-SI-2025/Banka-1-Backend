package com.banka1.order.entity.enums;

/**
 * Defines option direction type.
 * CALL = profit when market price increases above strike price.
 * PUT = profit when market price decreases below strike price.
 */
public enum OptionType {
    CALL,
    PUT
}
