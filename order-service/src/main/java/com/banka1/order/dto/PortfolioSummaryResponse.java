package com.banka1.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Summary response for the My Portfolio page.
 */
@Data
public class PortfolioSummaryResponse {
    private List<PortfolioResponse> holdings;
    private BigDecimal totalProfit;
    private BigDecimal yearlyTaxPaid;
    private BigDecimal monthlyTaxDue;
}
