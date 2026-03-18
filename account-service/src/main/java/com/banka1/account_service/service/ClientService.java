package com.banka1.account_service.service;


import com.banka1.account_service.dto.request.ApproveDto;
import com.banka1.account_service.dto.request.EditAccountLimitDto;
import com.banka1.account_service.dto.request.EditAccountNameDto;
import com.banka1.account_service.dto.request.NewPaymentDto;
import com.banka1.account_service.dto.response.AccountDetailsResponseDto;
import com.banka1.account_service.dto.response.AccountResponseDto;
import com.banka1.account_service.dto.response.CardResponseDto;
import com.banka1.account_service.dto.response.TransactionResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.jwt.Jwt;

public interface ClientService {
    String newPayment(Jwt jwt, NewPaymentDto newPaymentDto);
    String approveTransaction(Jwt jwt, Long id, ApproveDto newPaymentDto);
    Page<AccountResponseDto> findMyAccounts(Jwt jwt,int page,int size);
    Page<TransactionResponseDto> findAllTransactions(Jwt jwt,Long id,int page,int size);
    String editAccountName(Jwt jwt, Long id, EditAccountNameDto editAccountNameDto);
    String editAccountLimit(Jwt jwt, Long id, EditAccountLimitDto editAccountLimitDto);
    AccountDetailsResponseDto getDetails(Jwt jwt, Long id);
    Page<CardResponseDto> findAllCards(Jwt jwt,Long id,int page,int size);

}
