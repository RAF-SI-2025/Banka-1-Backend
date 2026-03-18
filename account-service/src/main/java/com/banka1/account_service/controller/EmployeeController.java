package com.banka1.account_service.controller;

import com.banka1.account_service.dto.request.*;
import com.banka1.account_service.dto.response.*;
import com.banka1.account_service.service.EmployeeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/employee")
//todo autorizacija
public class EmployeeController {

    private EmployeeService employeeService;

    @PostMapping("/accounts/checking")
    public ResponseEntity<String> createCheckingAccount(@AuthenticationPrincipal Jwt jwt,@RequestBody @Valid CheckingDto checkingDto) {
        return new ResponseEntity<>(employeeService.createCheckingAccount(jwt,checkingDto), HttpStatus.OK);
    }
    @PostMapping("/accounts/fx")
    public ResponseEntity<String> createFxAccount(@AuthenticationPrincipal Jwt jwt,@RequestBody @Valid FxDto fxDto) {
        return new ResponseEntity<>(employeeService.createFxAccount(jwt,fxDto), HttpStatus.OK);
    }

    @GetMapping("/accounts")
    public ResponseEntity<Page<AccountSearchResponseDto>> searchAllAccounts(@AuthenticationPrincipal Jwt jwt,
                                                                            @RequestParam(required = false) String imeVlasnikaRacuna,
                                                                            @RequestParam(required = false) String prezimeVlasnikaRacuna,
                                                                            @RequestParam(required = false) String accountNumber,
                                                                            @RequestParam(defaultValue = "0") @Min(value = 0) int page,
                                                                            @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size
    ) {
        return new ResponseEntity<>(employeeService.searchAllAccounts(jwt,imeVlasnikaRacuna,prezimeVlasnikaRacuna,accountNumber,page,size), HttpStatus.OK);
    }


    @PutMapping("/cards/{id}")
    public ResponseEntity<String> updateCard(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@RequestBody @Valid UpdateCardDto updateCardDto)
    {
        return new ResponseEntity<>(employeeService.updateCard(jwt,id,updateCardDto), HttpStatus.OK);
    }
}
