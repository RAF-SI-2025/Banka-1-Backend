package com.banka1.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO representing a portfolio position.
 */
@Data
public class PortfolioDto {
    private Long id;
    private Long userId;
    private Long listingId;
    private Integer quantity;
    private BigDecimal averagePurchasePrice;
}
