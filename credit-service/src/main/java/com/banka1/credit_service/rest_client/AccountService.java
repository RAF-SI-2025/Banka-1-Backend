package com.banka1.credit_service.rest_client;

import com.banka1.credit_service.dto.request.BankPaymentDto;
import com.banka1.credit_service.dto.request.PaymentDto;
import com.banka1.credit_service.dto.response.AccountDetailsResponseDto;
import com.banka1.credit_service.dto.response.InfoResponseDto;
import com.banka1.credit_service.dto.response.UpdatedBalanceResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AccountService {

    private final RestClient restClient;

    public AccountService(@Qualifier("accountClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public InfoResponseDto getInfo(String fromBankNumber, String toBankNumber) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/accounts/info")
                        .queryParam("fromBankNumber", fromBankNumber)
                        .queryParam("toBankNumber", toBankNumber)
                        .build())
                .retrieve()
                .body(InfoResponseDto.class);
    }

    public UpdatedBalanceResponseDto transfer(PaymentDto paymentDto) {
        return restClient.post()
                .uri("/internal/accounts/transfer")
                .body(paymentDto)
                .retrieve()
                .body(UpdatedBalanceResponseDto.class);
    }

    public UpdatedBalanceResponseDto transaction(PaymentDto paymentDto) {
        return restClient.post()
                .uri("/internal/accounts/transaction")
                .body(paymentDto)
                .retrieve()
                .body(UpdatedBalanceResponseDto.class);
    }

    public UpdatedBalanceResponseDto transactionFromBank(BankPaymentDto paymentDto) {
        return restClient.post()
                .uri("/internal/accounts/transactionFromBank")
                .body(paymentDto)
                .retrieve()
                .body(UpdatedBalanceResponseDto.class);
    }

    public AccountDetailsResponseDto getDetails(String accountNumber)
    {
        return restClient.get()
                .uri("/internal/accounts/{accountNumber}/details", accountNumber)
                .retrieve()
                .body(AccountDetailsResponseDto.class);
    }
}
