package com.banka1.order.dto;

import lombok.Data;

/**
 * DTO representing the status of a stock exchange.
 */
@Data
public class ExchangeStatusDto {
    /** Whether the exchange is open. */
    private Boolean open;

    /** Whether it's after hours. */
    private Boolean afterHours;

    /** Whether the exchange is closed. */
    private Boolean closed;

    public boolean isAfterHours() {
        return afterHours != null && afterHours;
    }
}
