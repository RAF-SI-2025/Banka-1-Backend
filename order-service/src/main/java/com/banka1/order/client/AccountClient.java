package com.banka1.order.client;

import com.banka1.order.dto.AccountDetailsDto;

/**
 * Client interface for communicating with the account-service.
 * Used to retrieve account details needed during order processing.
 */
public interface AccountClient {

    /**
     * Fetches details of a bank account by its account number.
     *
     * @param accountNumber the account number to look up
     * @return account details including balance and currency
     */
    AccountDetailsDto getAccountDetails(String accountNumber);
}
