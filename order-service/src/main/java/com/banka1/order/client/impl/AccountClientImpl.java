package com.banka1.order.client.impl;

import com.banka1.order.client.AccountClient;
import com.banka1.order.dto.AccountDetailsDto;
import com.banka1.order.dto.AccountTransactionRequest;
import com.banka1.order.dto.client.CreditDebitAccountDto;
import com.banka1.order.dto.client.CreditDebitBankDto;
import com.banka1.order.dto.client.PaymentDto;
import com.banka1.order.dto.response.UpdatedBalanceResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * RestClient-based implementation of {@link AccountClient}.
 * Active in all profiles except "local".
 */
@Component
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class AccountClientImpl implements AccountClient {

    private final RestClient accountRestClient;

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountDetailsDto getAccountDetails(String accountNumber) {
        return accountRestClient.get()
                .uri("/internal/accounts/{accountNumber}/details", accountNumber)
                .retrieve()
                .body(AccountDetailsDto.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountDetailsDto getAccountDetails(Long accountId) {
        if (looksLikeAccountNumber(accountId)) {
            return getAccountDetails(String.valueOf(accountId));
        }
        try {
            return getAccountDetailsByInternalId(accountId);
        } catch (HttpClientErrorException.NotFound ex) {
            return getAccountDetails(String.valueOf(accountId));
        }
    }

    private AccountDetailsDto getAccountDetailsByInternalId(Long accountId) {
        return accountRestClient.get()
                .uri("/internal/accounts/id/{accountId}/details", accountId)
                .retrieve()
                .body(AccountDetailsDto.class);
    }

    private boolean looksLikeAccountNumber(Long accountId) {
        return accountId != null && String.valueOf(accountId).length() >= 18;
    }

    @Override
    public AccountDetailsDto getGovernmentBankAccountRsd() {
        return accountRestClient.get()
                .uri("/internal/accounts/state/RSD")
                .retrieve()
                .body(AccountDetailsDto.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void transfer(AccountTransactionRequest request) {
        postTransaction(request).toBodilessEntity();
    }

    @Override
    public void debit(CreditDebitAccountDto request) {
        accountRestClient.post()
                .uri("/internal/accounts/debit")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void credit(CreditDebitAccountDto request) {
        accountRestClient.post()
                .uri("/internal/accounts/credit")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void debitBank(CreditDebitBankDto request) {
        accountRestClient.post()
                .uri("/internal/accounts/debitBank")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void creditBank(CreditDebitBankDto request) {
        accountRestClient.post()
                .uri("/internal/accounts/creditBank")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public UpdatedBalanceResponseDto transfer(PaymentDto payment) {
        return accountRestClient.post()
                .uri("/internal/accounts/transfer")
                .body(payment)
                .retrieve()
                .body(UpdatedBalanceResponseDto.class);
    }

    @Override
    public UpdatedBalanceResponseDto transaction(PaymentDto payment) {
        return postTransaction(payment).body(UpdatedBalanceResponseDto.class);
    }

    private RestClient.ResponseSpec postTransaction(Object payload) {
        return accountRestClient.post()
                .uri("/internal/accounts/transaction")
                .body(payload)
                .retrieve();
    }
}
