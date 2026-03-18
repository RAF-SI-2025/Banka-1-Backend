package com.banka1.account_service.service.implementation;

import com.banka1.account_service.domain.enums.Currency;
import com.banka1.account_service.dto.request.CheckingDto;
import com.banka1.account_service.dto.request.FxDto;
import com.banka1.account_service.dto.request.UpdateCardDto;
import com.banka1.account_service.dto.response.AccountSearchResponseDto;
import com.banka1.account_service.service.EmployeeService;
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class EmployeeServiceImplementation implements EmployeeService {
    @Override
    public String createFxAccount(Jwt jwt, FxDto fxDto) {
        if(fxDto.getCurrency() == Currency.RSD)
            throw new IllegalArgumentException("Ne moze RSD");
        return "";
    }

    @Override
    public String createCheckingAccount(Jwt jwt, CheckingDto checkingDto) {
        return "";
    }

    @Override
    public Page<AccountSearchResponseDto> searchAllAccounts(Jwt jwt, String imeVlasnikaRacuna, String prezimeVlasnikaRacuna, String accountNumber, int page, int size) {
        return null;
    }

    @Override
    public String updateCard(Jwt jwt, Long id, UpdateCardDto updateCardDto) {
        return "";
    }
}
