package com.banka1.credit_service.domain;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InterestRateStore {
    private BigDecimal nominalInterestRate;
    private BigDecimal effectiveInterestRate;

    public InterestRateStore(BigDecimal nominalInterestRate) {
        this.nominalInterestRate = nominalInterestRate;
    }
}
