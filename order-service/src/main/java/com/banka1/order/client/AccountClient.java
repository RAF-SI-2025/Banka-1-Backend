package com.banka1.order.client;

import com.banka1.order.dto.AccountDetailsDto;
import com.banka1.order.dto.AccountTransactionRequest;
import com.banka1.order.dto.client.PaymentDto;
import com.banka1.order.dto.response.UpdatedBalanceResponseDto;

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

    /**
     * Fetches details of a bank account by its ID.
     *
     * @param accountId the account ID to look up
     * @return account details including balance and currency
     */
    AccountDetailsDto getAccountDetails(Long accountId);

    /**
     * Legacy alias used by tax service.
     *
     * @param accountId the account ID to look up
     * @return account details including balance and currency
     */
    default AccountDetailsDto getAccountDetailsById(Long accountId) {
        return getAccountDetails(accountId);
    }

    /**
     * Fetches the government's RSD bank account details.
     *
     * @return government account details
     */
    AccountDetailsDto getGovernmentBankAccountRsd();

    /**
     * Performs a transaction between accounts.
     *
     * @param request the transaction request
     */
    void transfer(AccountTransactionRequest request);

    /**
     * Performs a legacy payment transaction used by the tax service.
     *
     * @param payment payment request
     * @return updated balances after the transfer
     */
    UpdatedBalanceResponseDto transaction(PaymentDto payment);
}
