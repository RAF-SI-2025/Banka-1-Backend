package com.banka1.stock_service.service;

import java.util.List;

/**
 * Shared ordered FX currency set used to derive the supported ordered-pair catalog.
 */
public final class ForexSupportedCurrencies {

    private static final List<String> VALUES = List.of(
            "RSD",
            "EUR",
            "CHF",
            "USD",
            "GBP",
            "JPY",
            "CAD",
            "AUD"
    );

    private ForexSupportedCurrencies() {
    }

    /**
     * Returns the supported currency codes in deterministic order.
     *
     * @return ordered supported FX currencies
     */
    public static List<String> values() {
        return VALUES;
    }
}
