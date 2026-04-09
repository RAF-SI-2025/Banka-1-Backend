package com.banka1.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Data Transfer Object representing a user's capital gains tax debt.
 *
 * <p>
 * Used for aggregating tax obligations per user.
 * Debt is always expressed in RSD (Serbian Dinar).
 * </p>
 *
 * <p>
 * This DTO is returned by:
 * <ul>
 *     <li>GET /api/tax/capital-gains/debts</li>
 *     <li>GET /api/tax/capital-gains/{userId}</li>
 * </ul>
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaxDebtResponse {

    /**
     * Unique identifier of the user.
     */
    private Long userId;

    /**
     * Total tax debt in RSD.
     */
    private BigDecimal debtRsd;
}