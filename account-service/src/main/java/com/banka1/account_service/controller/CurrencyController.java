package com.banka1.account_service.controller;

import com.banka1.account_service.domain.Currency;
import com.banka1.account_service.domain.enums.CurrencyCode;
import com.banka1.account_service.service.CurrencyService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/currencies")
@AllArgsConstructor
@PreAuthorize("hasAnyRole('CLIENT_BASIC','BASIC')")
public class CurrencyController {
    private CurrencyService currencyService;

    @GetMapping("/getAll")
    public ResponseEntity<List<Currency>> findAll(@AuthenticationPrincipal Jwt jwt){
            return new ResponseEntity<>(currencyService.findAll(), HttpStatus.OK);
    }
    @GetMapping("/getAllPage")
    public ResponseEntity<Page<Currency>> findAllPage(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam(defaultValue = "0") @Min(value = 0) int page,
                                                      @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size){
        return new ResponseEntity<>(currencyService.findAllPage(page,size), HttpStatus.OK);
    }
    @GetMapping()
    public ResponseEntity<Currency> findAllByCode(@AuthenticationPrincipal Jwt jwt,@RequestParam String code){
        return new ResponseEntity<>(currencyService.findByCode(CurrencyCode.valueOf(code.toUpperCase())), HttpStatus.OK);
    }

}
