package com.banka1.transfer.client.impl;

import com.banka1.transfer.client.AccountClient;
import com.banka1.transfer.dto.client.AccountDto;
import com.banka1.transfer.dto.client.PaymentDto;
import com.banka1.transfer.dto.client.UpdatedBalanceResponseDto;
import com.banka1.transfer.exception.BusinessException;
import com.banka1.transfer.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Implementacija klijenta za Account Service koja koristi RestClient.
 * Aktivna samo u profilima koji nisu "local".
 */
@Component
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class AccountClientImpl implements AccountClient {

    private final RestClient accountRestClient;

    @Override
    public AccountDto getAccountDetails(String accountNumber) {
        try {
            return accountRestClient.get()
                    .uri("/api/accounts/{accountNumber}", accountNumber)
                    .retrieve()
                    .body(AccountDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Račun " + accountNumber + " ne postoji.");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND, "Servis računa nije dostupan.");
        }
    }

    @Override
    public UpdatedBalanceResponseDto executeTransfer(PaymentDto paymentDto) {
        try {
            return accountRestClient.post()
                    .uri("/internal/accounts/transfer")
                    .body(paymentDto)
                    .retrieve()
                    .body(UpdatedBalanceResponseDto.class);
        } catch (HttpClientErrorException.BadRequest e) {
            // Ovde obično Account Service šalje informaciju o nedovoljno sredstava (400)
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS, "Neuspešan transfer: " + e.getResponseBodyAsString());
        } catch (HttpClientErrorException.Forbidden e) {
            throw new BusinessException(ErrorCode.ACCOUNT_OWNERSHIP_MISMATCH, "Account service: Narušeno vlasništvo nad računom.");
        } catch (Exception e) {
            log.error("Critical error during account transfer: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND, "Greška prilikom izvršavanja transfera u Account servisu.");
        }
    }
}
