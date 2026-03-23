package com.banka1.transaction_service.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AccountOwnershipType {
    PERSONAL(21),BUSINESS(22);
    private final int val;

}
