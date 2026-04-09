package com.banka1.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for creating a buy order.
 */
@Data
public class CreateBuyOrderRequest {
    /** ID of the security listing to buy. */
    @NotNull
    @Positive
    private Long listingId;

    /** Number of securities to buy. */
    @NotNull
    @Positive
    private Integer quantity;

    /** Limit price for LIMIT and STOP_LIMIT orders. */
    @Positive
    private BigDecimal limitValue;

    /** Stop price for STOP and STOP_LIMIT orders. */
    @Positive
    private BigDecimal stopValue;

    /** Whether the order must be filled completely or not at all. */
    private Boolean allOrNone = false;

    /** Whether to use margin for this order. */
    private Boolean margin = false;

    /** ID of the account to debit funds from. */
    @Positive
    private Long accountId;
}
