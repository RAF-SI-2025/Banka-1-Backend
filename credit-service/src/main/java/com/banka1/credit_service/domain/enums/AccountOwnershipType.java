package com.banka1.credit_service.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AccountOwnershipType {
    PERSONAL(21),BUSINESS(22);
    private final int val;

}
