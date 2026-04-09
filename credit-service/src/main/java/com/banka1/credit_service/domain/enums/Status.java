package com.banka1.credit_service.domain.enums;

import lombok.Getter;

@Getter
public enum Status {
    PENDING, APPROVED, DECLINED, ACTIVE, OVERDUE, PAID_OFF;
//    final boolean loanRequest;
//
//    Status(boolean loanRequest) {
//        this.loanRequest = loanRequest;
//    }


}
