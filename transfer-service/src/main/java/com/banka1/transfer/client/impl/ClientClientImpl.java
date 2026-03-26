package com.banka1.transfer.client.impl;

import com.banka1.transfer.client.ClientClient;
import com.banka1.transfer.dto.client.ClientInfoResponseDto;
import com.banka1.transfer.exception.BusinessException;
import com.banka1.transfer.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Implementacija klijenta za komunikaciju sa servisom klijenata putem REST API-ja.
 */
@Component
@Profile("!local") // Učitava se kad god profil nije "local"
@RequiredArgsConstructor
@Slf4j
public class ClientClientImpl implements ClientClient {

    private final RestClient clientRestClient;

    @Override
    public ClientInfoResponseDto getClientDetails(Long clientId) {
        try {
            return clientRestClient.get()
                    .uri("/customers/{id}", clientId)
                    .retrieve()
                    .body(ClientInfoResponseDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Client with ID {} not found", clientId);
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Klijent nije pronađen.");
        } catch (Exception e) {
            log.error("Client service error: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND, "Servis klijenata nije dostupan.");
        }
    }
}
