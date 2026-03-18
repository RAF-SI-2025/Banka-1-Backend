package com.banka1.account_service.service;

import com.banka1.account_service.dto.request.CheckingDto;
import com.banka1.account_service.dto.request.FxDto;
import com.banka1.account_service.dto.request.UpdateCardDto;
import com.banka1.account_service.dto.response.AccountSearchResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.jwt.Jwt;

public interface EmployeeService {
    String createFxAccount(Jwt jwt, FxDto fxDto);
    String createCheckingAccount(Jwt jwt, CheckingDto checkingDto);
    Page<AccountSearchResponseDto> searchAllAccounts(Jwt jwt,String imeVlasnikaRacuna,String prezimeVlasnikaRacuna,String accountNumber,int page,int size);
    String updateCard(Jwt jwt, Long id, UpdateCardDto updateCardDto);
}
