package com.banka1.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class TaxTrackingRowResponse {
    private String firstName;
    private String lastName;
    private String userType;
    private BigDecimal taxDebtRsd;
}
