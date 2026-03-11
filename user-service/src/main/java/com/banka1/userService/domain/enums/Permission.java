package com.banka1.userService.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public enum Permission {
    BANKING_BASIC("osnovno poslovanje banke"),
    CLIENT_MANAGE("upravljanje klientima"),
    SECURITIES_TRADE_LIMITED("trgovina hartijama sa berze uz limite"),
    SECURITIES_TRADE_UNLIMITED("trgovina hartijama sa berze bez limita"),
    TRADE_UNLIMITED("trgovina bez limita"),
    OTC_TRADE("OTC (over-the-counter) - trgovina za direktnu trgovinu akcijama i futures-ima"),
    FUND_AGENT_MANAGE("upravljanje fondovima i agentima"),
    EMPLOYEE_MANAGE_ALL("upravlja svim zaposlenima");
    @Setter
    private String description;


}
