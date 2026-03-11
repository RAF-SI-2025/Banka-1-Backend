package com.banka1.userService.domain.enums;

import lombok.Getter;

@Getter
public enum Role {

    BASIC(1),// osnovno upravljanje
    AGENT(2), // trgovina s hartijama
    SUPERVISOR(3), // otc...
    ADMIN(4); // upravljanje svim zaposlenima

    private final int power;
    Role(int power)
    {
        this.power=power;
    }
}
