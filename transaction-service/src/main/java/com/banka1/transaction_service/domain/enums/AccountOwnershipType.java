package com.banka1.transaction_service.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Enum representing the type of account ownership.
 */
@AllArgsConstructor
@Getter
public enum AccountOwnershipType {

    /** Personal account - owner is an individual */
    PERSONAL(21),

    /** Business account - owner is a legal entity/company */
    BUSINESS(22);

    /** Numeric value associated with the ownership type */
    private final int val;

}
