package com.banka1.transaction_service.rest_client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final RestClient clientServiceClient;

//    public ClientInfoResponseDto getUser(String jmbg) {
//        return clientServiceClient.get()
//                .uri("/customers/jmbg/{jmbg}", jmbg)
//                .retrieve()
//                .body(ClientInfoResponseDto.class);
//    }
//    public ClientInfoResponseDto getUser(Long id) {
//        return clientServiceClient.get()
//                .uri("/customers/{id}", id)
//                .retrieve()
//                .body(ClientInfoResponseDto.class);
//    }

}
