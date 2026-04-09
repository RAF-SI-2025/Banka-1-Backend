package com.banka1.transaction_service.rest_client;

import com.banka1.transaction_service.dto.request.PaymentDto;
import com.banka1.transaction_service.dto.response.AccountDetailsResponseDto;
import com.banka1.transaction_service.dto.response.InfoResponseDto;
import com.banka1.transaction_service.dto.response.UpdatedBalanceResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * REST client for interacting with the Account Service.
 * Provides methods for retrieving and updating account information.
 */
@Service
public class AccountService {

    private final RestClient restClient;

    /**
     * Constructor that injects the REST client for the Account Service.
     *
     * @param restClient configured REST client with JWT authentication
     */
    public AccountService(@Qualifier("accountClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Retrieves information about two accounts necessary for a transaction.
     *
     * @param fromBankNumber the source account number
     * @param toBankNumber the destination account number
     * @return information about both accounts (currencies, owners, contact details)
     */
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

    /**
     * Executes a money transfer between two accounts of the same owner.
     *
     * @param paymentDto DTO with transfer details
     * @return updated balances of both accounts
     */
    public UpdatedBalanceResponseDto transfer(PaymentDto paymentDto) {
        return restClient.post()
                .uri("/internal/accounts/transfer")
                .body(paymentDto)
                .retrieve()
                .body(UpdatedBalanceResponseDto.class);
    }

    /**
     * Executes a money transaction between two accounts of different owners.
     *
     * @param paymentDto DTO with transaction details
     * @return updated balances of both accounts
     */
    public UpdatedBalanceResponseDto transaction(PaymentDto paymentDto) {
        return restClient.post()
                .uri("/internal/accounts/transaction")
                .body(paymentDto)
                .retrieve()
                .body(UpdatedBalanceResponseDto.class);
    }

    /**
     * Retrieves account details by account number.
     *
     * @param accountNumber the account number to retrieve details for
     * @return the account details response
     */
    public AccountDetailsResponseDto getDetails(String accountNumber)
    {
        return restClient.get()
                .uri("/internal/accounts/{accountNumber}/details", accountNumber)
                .retrieve()
                .body(AccountDetailsResponseDto.class);
    }
}
