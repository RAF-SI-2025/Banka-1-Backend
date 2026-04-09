package com.banka1.order.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Optional request payload for supervisor-driven partial cancellation.
 */
@Data
public class PartialCancelOrderRequest {
    /** Quantity from the currently unfilled remainder to cancel. */
    @Positive
    private Integer quantity;
}
