package com.banka1.account_service.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AccountConcrete {
    STANDARDNI(AccountOwnershipType.PERSONAL),
    STEDNI(AccountOwnershipType.PERSONAL),
    PENZIONERSKI(AccountOwnershipType.PERSONAL),
    ZA_MLADE(AccountOwnershipType.PERSONAL),
    ZA_STUDENTE(AccountOwnershipType.PERSONAL),
    ZA_NEZAPOSLENE(AccountOwnershipType.PERSONAL),
    DOO(AccountOwnershipType.BUSINESS),
    AD(AccountOwnershipType.BUSINESS),
    FONDACIJA(AccountOwnershipType.BUSINESS);

    private final AccountOwnershipType accountOwnershipType;

}
