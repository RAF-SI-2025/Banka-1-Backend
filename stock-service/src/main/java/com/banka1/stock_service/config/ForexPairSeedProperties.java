package com.banka1.stock_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for seeding the built-in FX pair catalog.
 *
 * @param enabled whether the startup FX catalog seed should run automatically
 */
@Validated
@ConfigurationProperties(prefix = "stock.forex-seed")
public record ForexPairSeedProperties(
        boolean enabled
) {
}
