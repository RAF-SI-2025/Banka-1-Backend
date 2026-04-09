package com.banka1.credit_service.service;

import com.banka1.credit_service.domain.LoanRequest;
import com.banka1.credit_service.domain.enums.LoanType;
import com.banka1.credit_service.domain.enums.Status;
import com.banka1.credit_service.dto.request.LoanRequestDto;
import com.banka1.credit_service.dto.response.LoanInfoResponseDto;
import com.banka1.credit_service.dto.response.LoanRequestResponseDto;
import com.banka1.credit_service.dto.response.LoanResponseDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestParam;

public interface LoanService {
    LoanRequestResponseDto request(Jwt jwt, LoanRequestDto loanRequestDto);
    String confirmation(Jwt jwt,Long id,Status status);
    Page<LoanResponseDto> find(Jwt jwt,int page,int size);
    LoanInfoResponseDto info(Jwt jwt,Long id);
    Page<LoanRequest> findAllLoanRequest(Jwt jwt, LoanType vrstaKredita, String brojRacuna, int page, int size);
    Page<LoanResponseDto> findAllLoans(Jwt jwt, LoanType vrstaKredita, String brojRacuna, Status status,int page,int size);
}

