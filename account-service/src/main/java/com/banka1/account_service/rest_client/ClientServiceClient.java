package com.banka1.account_service.rest_client;

import com.banka1.account_service.dto.response.ClientIdResponseDto;
import com.banka1.account_service.dto.response.ClientResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class ClientServiceClient {

    private final RestClient clientServiceClient;

    public ClientIdResponseDto getUser(String jmbg) {
        return clientServiceClient.get()
                .uri("/customers/jmbg/{jmbg}", jmbg)
                .retrieve()
                .body(ClientIdResponseDto.class);
    }
    public Page<ClientResponseDto> searchClients(
            String ime,
            String prezime,
            int page,
            int size
    ) {
        String uri = UriComponentsBuilder.fromPath("/clients")
                .queryParamIfPresent("ime", java.util.Optional.ofNullable(ime))
                .queryParamIfPresent("prezime", java.util.Optional.ofNullable(prezime))
                .queryParam("page", page)
                .queryParam("size", size)
                .build()
                .toUriString();

        return clientServiceClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
