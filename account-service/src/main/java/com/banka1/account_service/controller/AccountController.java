package com.banka1.account_service.controller;

import com.banka1.account_service.dto.request.CheckingDto;
import com.banka1.account_service.dto.request.TransactionDto;
import com.banka1.account_service.dto.response.UpdatedBalanceResponseDto;
import com.banka1.account_service.service.AccountService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/accounts")
@PreAuthorize("hasRole('SERVICE')")
public class AccountController {

    private AccountService accountService;
    @PutMapping("/debit/{accountNumber}")
    public ResponseEntity<UpdatedBalanceResponseDto> debit(@AuthenticationPrincipal Jwt jwt, @PathVariable String accountNumber, @RequestBody @Valid TransactionDto transactionDto) {
        return new ResponseEntity<>(accountService.debit(jwt,accountNumber,transactionDto),HttpStatus.OK);
    }
    @PutMapping("/credit/{accountNumber}")
    public ResponseEntity<UpdatedBalanceResponseDto> credit(@AuthenticationPrincipal Jwt jwt,@PathVariable String accountNumber, @RequestBody @Valid TransactionDto transactionDto) {
        return new ResponseEntity<>(accountService.credit(jwt,accountNumber,transactionDto),HttpStatus.OK);
    }

}
