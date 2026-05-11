package com.banka1.order.service;

import com.banka1.order.dto.ActuaryProfitResponse;

import java.util.List;

public interface BankProfitService {

    List<ActuaryProfitResponse> getActuaryProfits();

    void refreshActuaryProfitRollup();
}
