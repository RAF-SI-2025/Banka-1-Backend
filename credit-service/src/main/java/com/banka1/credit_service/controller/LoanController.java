package com.banka1.credit_service.controller;

import com.banka1.credit_service.domain.LoanRequest;
import com.banka1.credit_service.domain.enums.InterestType;
import com.banka1.credit_service.domain.enums.LoanType;
import com.banka1.credit_service.domain.enums.Status;
import com.banka1.credit_service.dto.request.LoanRequestDto;
import com.banka1.credit_service.dto.response.LoanInfoResponseDto;
import com.banka1.credit_service.dto.response.LoanRequestResponseDto;
import com.banka1.credit_service.dto.response.LoanResponseDto;
import com.banka1.credit_service.service.LoanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loans")
//todo pretpostavljam da je ovo za klijente, ako nije menjaj

@AllArgsConstructor
public class LoanController {
    private LoanService loanService;

    @PreAuthorize("hasRole('CLIENT_BASIC')")
    @PostMapping("/requests")
    public ResponseEntity<LoanRequestResponseDto> requests(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid LoanRequestDto loanRequestDto)
    {
        return new ResponseEntity<>(loanService.request(jwt,loanRequestDto), HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('BASIC')")
    @PutMapping("/requests/{id}/approve")
    public ResponseEntity<String> approve(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id)
    {
        return new ResponseEntity<>(loanService.confirmation(jwt,id,Status.APPROVED),HttpStatus.OK);
    }
    @PreAuthorize("hasRole('BASIC')")
    @PutMapping("/requests/{id}/decline")
    public ResponseEntity<String> decline(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id)
    {

        return new ResponseEntity<>(loanService.confirmation(jwt,id,Status.DECLINED),HttpStatus.OK);
    }

    @PreAuthorize("hasRole('CLIENT_BASIC')")
    @GetMapping("/client")
    public ResponseEntity<Page<LoanResponseDto>> find(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue = "0") @Min(value = 0) int page, @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size)
    {
        return new ResponseEntity<>(loanService.find(jwt,page,size),HttpStatus.OK);
    }

    @PreAuthorize("hasAnyRole('CLIENT_BASIC','BASIC')")
    @GetMapping("/{loanNumber}")
    public ResponseEntity<LoanInfoResponseDto> info(@AuthenticationPrincipal Jwt jwt, @PathVariable Long loanNumber)
    {
        return new ResponseEntity<>(loanService.info(jwt,loanNumber),HttpStatus.OK);
    }

    @PreAuthorize("hasRole('BASIC')")
    @GetMapping("/requests")
    public ResponseEntity<Page<LoanRequest>> findAllLoanRequest(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String vrstaKredita, @RequestParam(required = false) String brojRacuna,@RequestParam(defaultValue = "0") @Min(value = 0) int page, @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size)
    {
        LoanType loanType;
        try {
            loanType=LoanType.valueOf(vrstaKredita);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Los loanType");
        }

        return new ResponseEntity<>(loanService.findAllLoanRequest(jwt,loanType,brojRacuna,page,size),HttpStatus.OK);
    }
    @PreAuthorize("hasRole('BASIC')")
    @GetMapping("/all")
    public ResponseEntity<Page<LoanResponseDto>> findAllLoans(@AuthenticationPrincipal Jwt jwt,@RequestParam(required = false) String vrstaKredita, @RequestParam(required = false) String brojRacuna,@RequestParam(required = false) String loanStatus,@RequestParam(defaultValue = "0") @Min(value = 0) int page, @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) int size)
    {
        LoanType loanType;
        try {
            loanType=LoanType.valueOf(vrstaKredita);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Los loanType");
        }
        Status status;
        try {
            status=Status.valueOf(loanStatus);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Los loanStatus");
        }
        return new ResponseEntity<>(loanService.findAllLoans(jwt,loanType,brojRacuna,status,page,size),HttpStatus.OK);
    }
}
