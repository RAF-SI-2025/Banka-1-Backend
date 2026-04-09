package com.banka1.credit_service.rest_client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ClientService {


    private final RestClient restClient;

    public ClientService(@Qualifier("userClient") RestClient restClient) {
        this.restClient = restClient;
    }

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
