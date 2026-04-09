package com.banka1.credit_service.domain.enums;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public enum LoanType {
    GOTOVINSKI(1.75),STAMBENI(1.50),AUTO(1.25),REFINANSIRAJUCI(1),STUDENTSKI(0.75);
    private final BigDecimal marza;

    LoanType(double marza) {
        this.marza = BigDecimal.valueOf(marza);
    }

}
