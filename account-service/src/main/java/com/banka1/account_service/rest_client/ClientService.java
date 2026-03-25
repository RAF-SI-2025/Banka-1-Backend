package com.banka1.account_service.rest_client;

import com.banka1.account_service.dto.response.ClientInfoResponseDto;
import com.banka1.account_service.dto.response.ClientResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final RestClient restClient;

    public ClientInfoResponseDto getUser(String jmbg) {
        return restClient.get()
                .uri("/customers/jmbg/{jmbg}", jmbg)
                .retrieve()
                .body(ClientInfoResponseDto.class);
    }
    public ClientInfoResponseDto getUser(Long id) {
        return restClient.get()
                .uri("/customers/{id}", id)
                .retrieve()
                .body(ClientInfoResponseDto.class);
    }

}
