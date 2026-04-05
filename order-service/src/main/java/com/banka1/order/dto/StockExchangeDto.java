package com.banka1.order.dto;

import lombok.Data;

/**
 * DTO representing a stock exchange as returned by the stock-service.
 */
@Data
public class StockExchangeDto {
    /** The exchange's unique identifier. */
    private Long id;
    /** Full name of the exchange (e.g. "New York Stock Exchange"). */
    private String name;
    /** Short acronym (e.g. "NYSE"). */
    private String acronym;
    /** Currency used for trading on this exchange (e.g. "USD"). */
    private String currency;
    /** Whether the exchange is currently open for trading. */
    private Boolean open;
}
