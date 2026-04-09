package com.banka1.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for creating a sell order.
 */
@Data
public class CreateSellOrderRequest {
    /** ID of the security listing to sell. */
    @NotNull
    @Positive
    private Long listingId;

    /** Number of securities to sell. */
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

    /** ID of the account to credit funds to. */
    @NotNull
    @Positive
    private Long accountId;
}
