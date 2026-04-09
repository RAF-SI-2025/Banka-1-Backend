package com.banka1.order.dto;

import lombok.Data;

/**
 * Request DTO used to set the number of shares
 * that will be publicly visible for OTC trading.
 *
 * Rules:
 * - Only applicable to STOCK positions
 * - publicQuantity cannot exceed total portfolio quantity
 * - If value is 0, position is no longer public
 */
@Data
public class SetPublicQuantityRequestDto {

    /**
     * Number of shares to make publicly available for trading.
     * Must be <= total quantity in portfolio.
     */
    private Integer publicQuantity;
}