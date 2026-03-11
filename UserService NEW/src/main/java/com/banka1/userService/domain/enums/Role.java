package com.banka1.userService.domain.enums;

import lombok.Getter;

@Getter
public enum Role {

    ADMIN(4), // upravljanje svim zaposlenima
    SUPERVISOR(3), // otc...
    AGENT(2), // trgovina s hartijama
    BASIC(1);// osnovno upravljanje
    private final int power;
    Role(int power)
    {
        this.power=power;
    }
}
