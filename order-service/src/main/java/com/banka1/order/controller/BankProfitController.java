package com.banka1.order.controller;

import com.banka1.order.dto.ActuaryProfitResponse;
import com.banka1.order.service.BankProfitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/bank-profit")
@RequiredArgsConstructor
public class BankProfitController {

    private final BankProfitService bankProfitService;

    @GetMapping("/actuaries")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<List<ActuaryProfitResponse>> getActuaryProfits() {
        return ResponseEntity.ok(bankProfitService.getActuaryProfits());
    }
}
