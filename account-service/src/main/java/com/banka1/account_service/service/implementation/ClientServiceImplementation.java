package com.banka1.account_service.service.implementation;

import com.banka1.account_service.dto.request.ApproveDto;
import com.banka1.account_service.dto.request.EditAccountLimitDto;
import com.banka1.account_service.dto.request.EditAccountNameDto;
import com.banka1.account_service.dto.request.NewPaymentDto;
import com.banka1.account_service.dto.response.AccountDetailsResponseDto;
import com.banka1.account_service.dto.response.AccountResponseDto;
import com.banka1.account_service.dto.response.CardResponseDto;
import com.banka1.account_service.dto.response.TransactionResponseDto;
import com.banka1.account_service.service.ClientService;
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class ClientServiceImplementation implements ClientService {
    @Override
    public String newPayment(Jwt jwt, NewPaymentDto newPaymentDto) {
        return "";
    }

    @Override
    public String approveTransaction(Jwt jwt, Long id, ApproveDto newPaymentDto) {
        return "";
    }


    @Override
    public Page<AccountResponseDto> findMyAccounts(Jwt jwt, int page, int size) {
        return null;
    }

    @Override
    public Page<TransactionResponseDto> findAllTransactions(Jwt jwt, Long id, int page, int size) {
        return null;
    }

    @Override
    public String editAccountName(Jwt jwt, Long id, EditAccountNameDto editAccountNameDto) {
        return "";
    }

    @Override
    public String editAccountLimit(Jwt jwt, Long id, EditAccountLimitDto editAccountLimitDto) {
        return "";
    }

    @Override
    public AccountDetailsResponseDto getDetails(Jwt jwt, Long id) {
        return null;
    }

    @Override
    public Page<CardResponseDto> findAllCards(Jwt jwt, Long id, int page, int size) {
        return null;
    }
}
